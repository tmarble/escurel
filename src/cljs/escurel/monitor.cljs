(ns escurel.monitor
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async
             :refer [<! >! chan put! timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [escurel.eraf :as eraf :refer [request-new-animation-loop]]))

(def frame-clock (atom {:tic 0 ;; number of times raf called back
                        :timestamp 0 ;; last raf timestamp
                        :delay 16.0  ;; average delay between raf
                        :samples nil ;; timestamps (during stats gathering)
                        :events {:next-id 0
                                 :next-tic 1073741824
                                 :timers nil}
                        }))

;; each timer looks like
;; {:id id
;;  :at-tic at-tic
;;  :callback callback
;;  :interval interval}

;; (eraf/slow-raf)

(defn last-timestamp []
  (:timestamp @frame-clock))

(defn set-timer [interval callback secs]
  (dec (:next-id (:events (swap! frame-clock
    (fn [frame]
      (let [{:keys [tic timestamp delay samples events]} frame
            {:keys [next-id next-tic timers]} events
            fire-interval (quot (* secs 1000.0) delay)
            at-tic (dec (+ tic fire-interval)) ;; faster!
            timer {:id next-id
                   :at-tic at-tic
                   :callback callback
                   :interval (if interval fire-interval)}]
        {:tic tic
         :timestamp timestamp
         :delay delay
         :samples samples
         :events {:next-id (inc next-id)
                  :next-tic (min next-tic at-tic)
                  :timers (conj timers timer)}})))))))

;; API similar to
;; https://developer.mozilla.org/en-US/Add-ons/SDK/High-Level_APIs/timers
;; except called back with current time
(defn set-interval [callback secs]
  ;; ersatz implementation
  ;; (js/setInterval #(callback (.now js/Date)) (* 1000.0 secs))
  (set-timer true callback secs))

(defn set-timeout [callback secs]
  ;; ersatz implementation
  ;; (js/setTimeout #(callback (.now js/Date)) (* 1000.0 secs))
  (set-timer false callback secs))

(defn remove-timer [id]
  (swap! frame-clock
    (fn [frame]
      (let [{:keys [tic timestamp delay samples events]} frame
            {:keys [next-id next-tic timers]} events
            new-timers (filter #(not= (:id %) id) timers)
            new-next-tic (if (empty? new-timers)
                           1073741824
                           (reduce min (map :at-tic new-timers)))]
        {:tic tic
         :timestamp timestamp
         :delay delay
         :samples samples
         :events {:next-id next-id
                  :next-tic new-next-tic
                  :timers new-timers}}))))

(defn refresh-timer [rid]
  (swap! frame-clock
    (fn [frame]
      (let [{:keys [tic timestamp delay samples events]} frame
            {:keys [next-id next-tic timers]} events
            old-timers (filter #(not= (:id %) rid) timers)
            old-timer (first (filter #(= (:id %) rid) timers))
            {:keys [id at-tic callback interval]} old-timer
            new-timer {:id id
                       :at-tic (+ tic interval)
                       :callback callback
                       :interval interval}
            new-timers (conj old-timers new-timer)
            new-next-tic (if (empty? new-timers)
                           1073741824
                           (reduce min (map :at-tic new-timers)))]
        {:tic tic
         :timestamp timestamp
         :delay delay
         :samples samples
         :events {:next-id next-id
                  :next-tic new-next-tic
                  :timers new-timers}}))))

(defn clear-interval [id]
  ;; ersatz implementation
  ;; (js/clearInterval id)
  (remove-timer id))

(defn clear-timeout [id]
  ;; ersatz implementation
  ;; (js/clearTimeout id)
  (remove-timer id))

(defn get-component-action [default thread-chan threads notfound]
  (if (= thread-chan :default)
    [default #()]
    (let [thread (first (filter
                          #(identical? (:chan %) thread-chan)
                          threads))]
      (if thread
        [(:component thread) (:action thread)]
        [notfound #()]))))

;; take samples for one second then use that
(defn calc-new-frame [frame new-timestamp]
  (let [{:keys [tic timestamp delay samples events]} frame
        new-tic (inc tic)
        new-samples (if (or (zero? timestamp) (>= tic 60))
                      nil
                      (let [d (- new-timestamp timestamp)
                            prev-samples (take 9 samples)]
                        (cons d prev-samples)))
        new-delay (if (empty? new-samples)
                    delay ;; use old estimate
                    (/ (reduce + new-samples) (count new-samples)))
        new-frame {:tic new-tic
                   :timestamp new-timestamp
                   :delay new-delay
                   :samples new-samples
                   :events events}]
    new-frame))

(defn monitor-request [monitor monitor-chan new-timestamp]
  (let [new-frame (swap! frame-clock #(calc-new-frame % new-timestamp))]
    (put! monitor-chan new-frame)))

(defn monitor-action [monitor frame]
  (if (map? frame)
  (let [{:keys [tic timestamp delay events]} frame
        {:keys [next-id next-tic timers]} events]
    (when (>= tic next-tic)
      (let [ready-timers (filter #(>= tic (:at-tic %)) timers)]
        (doseq [ready ready-timers]
          ((:callback ready) timestamp)
          (if (nil? (:interval ready))
            (remove-timer (:id ready))
            (refresh-timer (:id ready)))))))))

;; monitor-view is an Om component
(defn monitor-view [monitor owner]
  (reify
    om/IInitState
    (init-state [_]
      (let [ch (chan (async/sliding-buffer 1))
            request (fn [value]
                      (monitor-request monitor ch value))
            action (fn [value]
                     (monitor-action monitor value))
            thread {:component :monitor
                    :chan ch
                    :action action}]
        (om/transact! monitor [:threads] #(conj % thread))
        (request-new-animation-loop request))
      {:init true})
    om/IWillMount
    (will-mount [_]
      (go (while true
            (let [threads (:threads @monitor)
                  thread-chans (map :chan threads)
                  [value thread-chan] (alts! thread-chans)
                  [component action] (get-component-action value thread-chan threads :idle)]
              (if (= component :idle)
                (println "hey we should NEVER be idle!")
                (action value))))))
    om/IRender
    (render [_]
      (dom/span nil ""))))
