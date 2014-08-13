(ns escurel.monitor
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async
             :refer [<! >! chan put! timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [escurel.eraf :as eraf :refer [request-animation-frame]]))

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


(def ^{:dynamic true :private true} *raf* (chan (async/sliding-buffer 1)))

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
        ;; (println "new timeout" timer) ;; debug
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
    ;; (println "calc-new-frame:" new-frame)
    new-frame
    ))

(defn update-frame-clock [new-timestamp]
  (let [new-frame (swap! frame-clock #(calc-new-frame % new-timestamp))]
    (go (>! *raf* new-frame))
    (request-animation-frame update-frame-clock)))

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

(defn raf-action [tag app frame]
  (let [{:keys [tic timestamp delay events]} frame
        {:keys [next-id next-tic timers]} events]
    ;; (when (zero? (mod tic 60)) ;; debug
    ;;   (println "raf:" frame)
    ;;   )
    (when (>= tic next-tic)
      (let [ready-timers (filter #(>= tic (:at-tic %)) timers)]
        (doseq [ready ready-timers]
          ;; (println "firing timer id: " (:id ready) "now:" (.now js/Date))
          ((:callback ready) timestamp)
          (if (nil? (:interval ready))
            (remove-timer (:id ready))
            (refresh-timer (:id ready))))))))

(defn get-tag-action [default thread-chan threads notfound]
  (if (= thread :default)
    [default #()]
    (let [tags (keys threads)
          tag (first (filter
                          #(if (= (:chan (get threads %)) thread-chan) %)
                          tags))
          thread (get threads tag)]
      (if thread
        [(:tag thread) (:action thread)]
        [notfound #()]))))

;; monitor-view is an Om component
(defn monitor-view [app owner]
  (reify
    om/IInitState
    (init-state [_]
      (om/transact! app [:threads]
        #(assoc % :raf {:chan *raf* :action raf-action}))
      (request-animation-frame update-frame-clock)
      {:init true})
    om/IWillMount
    (will-mount [_]
      (go (while true
            (let [threads (:threads @app)
                  thread-chans (vec (map :chan (vals threads)))
                  [v thread-chan] (alts! thread-chans)
                  [tag action] (get-tag-action v thread-chan threads :idle)]
              ;; (println "tag" tag "thread" thread "val" v) ;; debug
              (if (= tag :idle)
                (println "hey we should NEVER be idle!")
                (action tag app v))))))
    om/IRender
    (render [_]
      (dom/span nil "")))) ;; "Monitor"
