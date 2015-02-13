(ns escurel.client
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async
             :refer [<! >! chan put! timeout]]
            [chord.client :refer [ws-ch]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [goog.dom :as gdom]
            [escurel.monitor :as monitor
             :refer [last-timestamp monitor-view set-timeout set-interval]]))

(enable-console-print!)

(def app-state
  (atom {:monitor { ;; Om component
                   :threads []
                   }
         :login {:user nil
                 :status :loggedout
                 } ;; cursor for login-view
         :tic-toc {:tic true} ;; cursor for tic-toc-view
         :say-something {:label "Enter a message:"
                         :msg "" } ;; cursor for say-something
         }))

(defn add-monitor-thread [thread]
  (swap! app-state
    (fn [app]
      (let [monitor (:monitor app)
            threads (:threads monitor)
            new-threads (conj threads thread)
            new-monitor (assoc monitor :threads new-threads)]
        (assoc app :monitor new-monitor)))))

(defn display [show]
  (if show
    #js {}
    #js {:display "none"}))

(defn hspacer []
  (dom/span #js {:className "hspacer"} "  "))

(defn login-action [login user]
  (println "login-action:" user)
  (om/transact! login
    (fn [state]
      (condp = user
        :loggedin {:user nil :status :loggedout} ;; cannot request this state
        :loggedout {:user nil :status :loggedout}
        :sqrl (do
                (set-timeout #(login-action login :sqrl-timeout) 10.0)
                {:user nil :status :sqrl})
        :sqrl-timeout (if (= (:status state) :loggedin)
                        state
                        {:user nil :status :loggedout}) ;; timeout
        ;; just for debugging...
        {:user user :status :loggedin}))))

;; login-view is an Om component
(defn login-view [login owner]
  (reify
    om/IInitState
    (init-state [_]
      (let [ch (chan 1)
            request (fn [e value]
                      (println "login-request" value)
                      (put! ch value)
                      ;; "Warning: Returning `false` from an event
                      ;; handler is deprecated and will be ignored in
                      ;; a future release. Instead, manually call
                      ;; e.stopPropagation() or e.preventDefault(), as
                      ;; appropriate."
                      (.preventDefault e)
                      (.stopPropagation e))
            action (fn [value]
                     (login-action login value))
            thread {:component :login
                    :chan ch
                    :action action}]
        (add-monitor-thread thread)
        {:request request}))
    om/IRenderState
    (render-state [_ {:keys [request]}]
      (let [{:keys [user status]} login]
        (dom/div #js {:id "login-view"}
          (dom/span #js {:style (display (= status :loggedout))}
            "Please log in ")
          (dom/button #js {:style (display (= status :loggedout))
                           :onClick #(request % :sqrl)}
            "Log in")
          (dom/span #js {:style (display (= status :sqrl))}
            "Please use SQRL to login now...")
          (dom/button #js {:style (display (= status :sqrl))
                           :onClick #(request % "fred")}
            "SQRL")
          (dom/button #js {:style (display (= status :sqrl))
                           :onClick #(request % :loggedout)}
            "Cancel")
          (dom/span #js {:style (display (= status :loggedin))}
            "Click to log out ")
          (dom/button #js {:style (display (= status :loggedin))
                           :onClick #(request % :loggedout)}
            "Log out")
          )))))

;; tic-toc-view is an Om component
(defn tic-toc-view [tic-toc owner]
  (reify
    om/IInitState
    (init-state [_]
      (let [tic-id (set-interval #(om/transact! tic-toc [:tic] not) 1.0)
            start-time (.now js/Date) ;; debug
            timeout-id (set-timeout #(println "done at:" % ;; debug
                                       "elapsed:"
                                       (/ (- (.now js/Date) start-time) 1000.0))
                         10.0)]
        (println "tic-id:" tic-id)
        (println "timeout-id:" timeout-id "at:" start-time) ;; debug
        )
      {:init true})
    om/IRender
    (render [_]
      (dom/span nil
        (if (:tic tic-toc)
          "tic"
          "toc")))))

(defn edit-change [e data edit-key owner]
  (om/transact! data edit-key (fn [_] (.. e -target -value)))
  (.preventDefault e)
  (.stopPropagation e))

;; cb is the on-edit callback (a closure bound to the :class/id)
(defn edit-end [e text owner cb]
  ;; (println "edit-end")
  (om/set-state! owner :editing false)
  (cb text)
  (.preventDefault e)
  (.stopPropagation e))

;; in the Intermediate tutorial this function is used to
;; persist changes.. here just print the update on the console
(defn edit-callback [cursor owner text]
  (let [value (om/value cursor)
        label (:label value)
        next-label "You said:"
        threads (get-in @app-state [:monitor :threads])
        thread (first (filter #(= (:component %) :app) threads))
        ws-channel (:chan thread)]
    (when-not (= label next-label)
      (om/update! cursor [:label] next-label))
    (println "sending:" text)
    (if ws-channel
      (put! ws-channel text)
      (println "unable to send to server"))))

;; Om editable component
(defn editable [data owner {:keys [edit-label edit-key edit-button on-edit] :as opts}]
  (reify
    om/IInitState
    (init-state [_]
      {:editing false})
    om/IRenderState
    (render-state [_ {:keys [editing]}]
      (let [label (get data edit-label)
            text (get data edit-key)
            button (or edit-button "Edit")
            edit-cb (or on-edit #(edit-callback data owner %))]
        (dom/div nil
          (when-not (empty? label)
            (dom/span #js {:className "edit-label" } label))
          (hspacer)
          (dom/span #js {:style (display (not editing))} text)
          (dom/input
            #js {:style (display editing)
                 :value text
                 :onChange #(edit-change % data edit-key owner)
                 :onKeyDown #(when (= (.-key %) "Enter")
                               (edit-end % text owner edit-cb))
                 :onBlur #(when (om/get-state owner :editing)
                            (edit-end % text owner edit-cb))})
          (hspacer)
          (dom/button
            #js {:style (display (not editing))
                 :onClick #(om/set-state! owner :editing true)}
            button))))))

;; message from the server
(defn app-action [ws-channel app value]
  ;; {:keys [message error]}
  (println "server:" value))

;; overall application component
(defn app-view [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (go ;; performance problem?
        (let [ws-read (chan (async/buffer 3))
              ws-write (chan (async/buffer 3))
              {:keys [ws-channel error]} (<! (ws-ch "ws://localhost:8080/ws"
                                               {:format :transit-json
                                                :read-ch ws-read
                                                :write-ch ws-write}))
              ;; ws-channel nil
              ;; error "not initialized"
              action (fn [value]
                       (app-action ws-channel app value))
              thread {:component :app
                      :chan ws-channel
                      :action action}]
          (println "web socket initialized " (nil? ws-channel))
          (if error
            (println "error initializing web socket:" error)
            (when ws-channel
              (om/transact! app [:monitor :threads] #(conj % thread))
              (>! ws-channel "I am a new client") ;; go
              (println "sent first message")))
          (println "INIT app-view"))
        ) ;; go
      )
    om/IRender
    (render [_]
      (let [{:keys [monitor login tic-toc say-something]} app] ;; cursors
        (dom/div #js {:id "app-view"}
          (dom/h1 nil "escurel")
          (om/build login-view login)
          (om/build monitor-view monitor)
          (dom/hr nil) ;;-- DEBUG stuff below ------------------
          (dom/ul nil
            (dom/li nil (om/build tic-toc-view tic-toc))
            (dom/li nil (om/build editable say-something
                          {:opts {:edit-label :label
                                  :edit-key :msg
                                  :edit-button "Compose"}}))
            ))
        ))))

(om/root app-view app-state {:target (gdom/getElement "app")})
