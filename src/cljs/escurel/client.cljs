(ns escurel.client
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async
             :refer [<! >! chan put! timeout]]
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
         :tic-toc2 {:tic true} ;; cursor for tic-toc-view
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
            request (fn [value]
                      (println "login-request" value)
                      (put! ch value))
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
                           :onClick #(request :sqrl)}
            "Log in")
          (dom/span #js {:style (display (= status :sqrl))}
            "Please use SQRL to login now...")
          (dom/button #js {:style (display (= status :sqrl))
                           :onClick #(request "fred")}
            "SQRL")
          (dom/button #js {:style (display (= status :sqrl))
                           :onClick #(request :loggedout)}
            "Cancel")
          (dom/span #js {:style (display (= status :loggedin))}
            "Click to log out ")
          (dom/button #js {:style (display (= status :loggedin))
                           :onClick #(request :loggedout)}
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

;; overall application component
(defn app-view [app owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [monitor login tic-toc tic-toc2]} app] ;; cursors
        (dom/div #js {:id "app-view"}
          (dom/h1 nil "escurel")
          (om/build login-view login)
          (om/build monitor-view monitor)
          (dom/hr nil) ;;-- DEBUG stuff below ------------------
          (dom/ul nil
            (dom/li nil (om/build tic-toc-view tic-toc))
            (dom/li nil (om/build tic-toc-view tic-toc2))
            (dom/li nil "send server message via ws")
            (dom/li nil "receive server message via ws")
            (dom/li nil "use transit")
            ))
        ))))

(om/root app-view app-state {:target (gdom/getElement "app")})
