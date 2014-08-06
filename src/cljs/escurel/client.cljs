(ns escurel.client
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async
             :refer [<! >! chan put! timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [goog.dom :as gdom]))

(enable-console-print!)

(def login-delay 10)

(def app-state
  (atom {:login [0] ;; cursor for login-view
         :tic-toc {:tic true} ;; cursor for tic-toc-view
         }))

(defn display [show]
  (if show
    #js {}
    #js {:display "none"}))

;; make login-view an Om component
(defn login-view [login owner]
  (reify
    om/IRender
    (render [_]
      (let [login-value (first login)
            loggedin? (< login-value 0)
            loggedout? (zero? login-value)
            sqrl? (> login-value 0)]
        (dom/div #js {:id "login-view"}
          (dom/span #js {:style (display loggedout?)} "Please log in ")
          (dom/button #js {:style (display loggedout?)
                           :onClick #(om/update! login [0] login-delay)}
            "Log in")
          (dom/span #js {:style (display sqrl?)}
            (str "Please use SQRL to login now... [" login-value "] "))
          (dom/button #js {:style (display sqrl?)
                           :onClick #(om/update! login [0] -1)}
            "SQRL")
          (dom/button #js {:style (display sqrl?)
                           :onClick #(om/update! login [0] 0)}
            "Cancel")
          (dom/span #js {:style (display loggedin?)} "Click to log out ")
          (dom/button #js {:style (display loggedin?)
                           :onClick #(om/update! login [0] 0)}
            "Log out")
          )))))

;; make tic-toc an Om component
(defn tic-toc-view [tic-toc owner]
  (reify
    om/IRender
    (render [_]
      (dom/span nil
        (if (:tic tic-toc)
          "tic"
          "toc")))))

;; make monitor an Om component
(defn monitor-view [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:foo false})
    om/IWillMount
    (will-mount [_]
      (go (while true
            (<! (timeout 1000))
            ;; (println "ticking..." (om/rendering?))
            (om/transact! app [:tic-toc :tic] not)
            (om/transact! app [:login]
              (fn [login]
                (let [login-value (first login)]
                  (if (> login-value 0)
                    [(dec login-value)]
                    login))))
            )))
    om/IRenderState
    (render-state [_ {:keys [foo]}]
      (dom/span nil "Monitor"))))

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
            (dom/li nil "checking 3/10")
            (dom/li nil "field update via channel")
            (dom/li nil "send server message via ws")
            (dom/li nil "receive server message via ws")
            (dom/li nil "use transit")
            ))
        ))))

(om/root app-view app-state {:target (gdom/getElement "app")})
