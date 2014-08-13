(ns escurel.eraf)

(def ^{:dynamic true} *eraf-delay* 16.6)

(defn ersatzAnimationFrame [callback]
  ;; coerce to float as the real raf
  (callback (float (.now js/Date))))

(defn ersatzRequestAnimationFrame [callback]
  (js/setTimeout #(ersatzAnimationFrame callback) *eraf-delay*))

(def ^{:dynamic true} request-animation-frame
  (cond
    (exists? js/requestAnimationFrame) js/requestAnimationFrame
    true ersatzRequestAnimationFrame))

;; this function is exceedingly useful to debug raf
;; events in slow motion
(defn slow-raf [&[delay]]
  (set! request-animation-frame ersatzRequestAnimationFrame)
  (let [d (if delay delay 996)]
    (set! *eraf-delay* d)))
