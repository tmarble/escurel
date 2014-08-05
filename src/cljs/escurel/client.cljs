(ns escurel.client
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [goog.dom :as gdom]))

(enable-console-print!)

(def app-state
  (atom {:login 0} ))

(defn display [show]
  (if show
    #js {}
    #js {:display "none"}))

(defn app-view [app owner]
  (reify
    om/IRender
    (render [_]
      (dom/span nil (str "login state: " (:login app))))))

(om/root app-view app-state {:target (gdom/getElement "app")})
