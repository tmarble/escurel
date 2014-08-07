(ns escurel.client
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async
             :refer [<! >! chan put! timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [goog.dom :as gdom]))

(enable-console-print!)

(def login-delay 10)
(def max-clock 1073741824)
(def ms-per-update 40)
(def tics-per-sec (/ 1000 ms-per-update))

(def clock (atom {:tic 0 :time 0}))

(def app-state
  (atom {:threads {} ;; core.async channels
         :tic-toc {:tic true} ;; cursor for tic-toc-view
         :example {:checking false :runat 0} ;; cursor for example-view
         :login {:user nil :runat 0} ;; cursor for login-view
         }))

(defn display [show]
  (if show
    #js {}
    #js {:display "none"}))

(defn example-check []
  (println "example-check")
  (put!
    (get-in @app-state [:threads :example])
    (+ (:tic @clock) (* 3 tics-per-sec))))

(defn login-offer [yes]
  (println "login-offer: " yes)
  (let [runat (if (string? yes)
                yes
                (if (true? yes)
                  (+ (:tic @clock) (* 10 tics-per-sec))
                  0))]
    (put! (get-in @app-state [:threads :login]) runat)))

;; make login-view an Om component
(defn login-view [login owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [user runat]} login
            loggedin? (not (nil? user))
            loggedout? (and (nil? user) (zero? runat))
            sqrl? (not (or loggedin? loggedout?))]
        (dom/div #js {:id "login-view"}
          (dom/span #js {:style (display loggedout?)} "Please log in ")
          (dom/button #js {:style (display loggedout?)
                           :onClick #(do (example-check)
                                         (login-offer true))}
            "Log in")
          (dom/span #js {:style (display sqrl?)}
            "Please use SQRL to login now...")
          (dom/button #js {:style (display sqrl?)
                           :onClick #(login-offer "fred")}
            "SQRL")
          (dom/button #js {:style (display sqrl?)
                           :onClick #(login-offer false)}
            "Cancel")
          (dom/span #js {:style (display loggedin?)} "Click to log out ")
          (dom/button #js {:style (display loggedin?)
                           :onClick #(login-offer false)}
            "Log out")
          )))))

;; make example an Om component
(defn example-view [example owner]
  (reify
    om/IRender
    (render [_]
      (dom/span nil
        (if (:checking example)
          (str "example CHECKING runat: " (:runat example))
          (str "example runat: "  (:runat example)))))))

;; make tic-toc an Om component
(defn tic-toc-view [tic-toc owner]
  (reify
    om/IRender
    (render [_]
      (dom/span nil
        (if (:tic tic-toc)
          "tic"
          "toc")))))

(defn init-threads [threads]
  (println "init-threads")
  (let [example (chan 1)
        login (chan 1)]
    (merge threads {:example example :login login})
    ))

(defn clock-run [app threads]
  (let [nclock (swap! clock
                 (fn [c]
                   {:tic (inc (:tic c)) :time (.now js/Date)}))
        tic (:tic nclock)]
    (when (zero? (mod tic tics-per-sec))
      (om/transact! app [:tic-toc :tic] not))
    (doseq [thread threads]
      (let [key (first thread)
            threadstate (get @app-state key)
            runat (:runat threadstate)]
        (when (and runat (not (zero? runat)) (>= tic runat))
          (println "it's time to run " key)
          (put! (get-in @app-state [:threads key]) 0))
        ))))

(defn next-tic []
  (let [time (:time @clock)
        nexttime (+ time ms-per-update)
        now (.now js/Date)]
    (if (>= now nexttime)
      0
      (- nexttime now))))

(defn example-run [app new-runat]
  (om/transact! app [:example]
    (fn [example]
      (let [{:keys [checking runat]} example]
        (if checking
          (do(println "STOP checking, new-runat: " new-runat "runat:" runat)
              {:checking false :runat 0})
          (do (println "START checking, new-runat: " new-runat)
              {:checking true :runat new-runat}))))))

(defn login-run [app new-runat]
  (println "login-run:" new-runat)
  (om/transact! app [:login]
    (fn [login]
      (let [{:keys [user runat]} login]
        (if (string? new-runat)
          {:user new-runat :runat 0}
          (if (zero? new-runat)
            {:user nil :runat 0}
            {:user nil :runat new-runat}))))))

(defn get-thread-key [default thread threads notfound]
  (if (= thread :default)
    default
    (let [key (first (first (filter
                              #(if (= (second %) thread) (first %))
                              threads)))]
      (if key
        key
        notfound))))

;; make monitor an Om component
(defn monitor-view [app owner]
  (reify
    om/IInitState
    (init-state [_]
      (om/transact! app [:threads] init-threads)
      {:init true})
    om/IWillMount
    (will-mount [_]
      (go (loop [refresh (timeout (next-tic))]
            (let [threads (:threads @app)
                  channels (conj (vals threads) refresh)
                  ;; [v thread] (alts! channels :default :idle)
                  [v thread] (alts! channels)
                  key (get-thread-key v thread threads :clock)]
              ;; (println "key" key "thread" thread "val" v)
              (condp = key
                :clock (do (clock-run app threads)
                           (recur (timeout (next-tic))))
                :login (do (login-run app v)
                           (recur refresh))
                :example (do (example-run app v)
                             (recur refresh))
                :idle (println "hey we should NEVER be idle!")
                )
              ) ;; let
            )))
    om/IRender
    (render [_]
      (dom/span nil "Monitor"))))

;; overall application component
(defn app-view [app owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [login tic-toc example]} app] ;; cursors
        (dom/div #js {:id "app-view"}
          (dom/h1 nil "escurel")
          (om/build login-view login)
          (om/build monitor-view app)
          (dom/hr nil) ;;-- DEBUG stuff below ------------------
          (dom/ul nil
            (dom/li nil (om/build tic-toc-view tic-toc))
            (dom/li nil (om/build example-view example))
            (dom/li nil "field update via channel")
            (dom/li nil "send server message via ws")
            (dom/li nil "receive server message via ws")
            (dom/li nil "use transit")
            ))
        ))))

(om/root app-view app-state {:target (gdom/getElement "app")})
