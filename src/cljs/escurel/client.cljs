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
  (atom {:threads {} ;; {tag {:chan chan :action action}}
         :tic-toc {:tic true} ;; cursor for tic-toc-view
         :login {:user nil
                 :status :loggedout
                 } ;; cursor for login-view
         }))

(defn display [show]
  (if show
    #js {}
    #js {:display "none"}))

(defn login-offer [user]
  (println "login-offer: " user)
  ;; change login state
  ;; add timeout if necessary
  ;; put! value in the login channel
  )

(defn login-action [tag app v]
  ;; take v from the login channel
  )

;; login-view is an Om component
(defn login-view [login owner]
  (reify
    om/IInitState
    (init-state [_]
      ;; TODO add thread
      ;; (om/transact! app [:threads]
      ;;   #(assoc % :login {:chan login :action login-action}))
      {:init true})
    om/IRender
    (render [_]
      (let [{:keys [user status]} login]
        (dom/div #js {:id "login-view"}
          (dom/span #js {:style (display (= status :loggedout))}
            "Please log in ")
          (dom/button #js {:style (display (= status :loggedout))
                           :onClick #(login-offer true)}
            "Log in")
          (dom/span #js {:style (display (= status :sqrl))}
            "Please use SQRL to login now...")
          (dom/button #js {:style (display (= status :sqrl))
                           :onClick #(login-offer "fred")}
            "SQRL")
          (dom/button #js {:style (display (= status :sqrl))
                           :onClick #(login-offer false)}
            "Cancel")
          (dom/span #js {:style (display (= status :loggedin))}
            "Click to log out ")
          (dom/button #js {:style (display (= status :loggedin))
                           :onClick #(login-offer false)}
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
      (let [{:keys [login tic-toc]} app] ;; cursors
        (dom/div #js {:id "app-view"}
          (dom/h1 nil "escurel")
          (om/build login-view login)
          (om/build monitor-view app)
          (dom/hr nil) ;;-- DEBUG stuff below ------------------
          (dom/ul nil
            (dom/li nil (om/build tic-toc-view tic-toc))
            (dom/li nil "send server message via ws")
            (dom/li nil "receive server message via ws")
            (dom/li nil "use transit")
            ))
        ))))

(om/root app-view app-state {:target (gdom/getElement "app")})
