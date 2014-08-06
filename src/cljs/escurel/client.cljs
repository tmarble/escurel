(ns escurel.client
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [goog.dom :as gdom]))

(enable-console-print!)

(def login-delay 10)

(def app-state
  (atom {:login [0]} ))

(defn display [show]
  (if show
    #js {}
    #js {:display "none"}))

(defn countdown [login & [delay]]
  (let [login-value (first @login)
        again #(countdown login)]
    (cond
      delay (do (om/update! login [0] delay)
                (js/setTimeout again 1000))
      (> login-value 0) (do (om/transact! login [0] dec)
                               (js/setTimeout again 1000))
      ;; 0 -> we will be logged out
      ;; -1 -> we are logged in -- don't force a logout here
      )))

(defn app-view [app owner]
  (reify
    om/IRender
    (render [_]
      (let [login (:login app) ;; cursor
            login-value (first login)
            loggedin? (< login-value 0)
            loggedout? (zero? login-value)
            sqrl? (> login-value 0)]
        (dom/div #js {:id "login"}
          (dom/h1 nil "escurel")
          (dom/span #js {:style (display loggedout?)} "Please log in ")
          (dom/button #js {:style (display loggedout?)
                           :onClick #(countdown login login-delay)}
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

(om/root app-view app-state {:target (gdom/getElement "app")})
