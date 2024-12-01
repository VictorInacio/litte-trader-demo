(ns org.clojars.victorinacio.strategy
  (:require [org.clojars.victorinacio.price :as price]
            [clojure.core.async
             :as async
             :refer [>! <! >!! <!! chan close!
                     go go-loop put! take!
                     transduce-chan
                     dropping-buffer sliding-buffer]]
            [clojure.pprint :as pp]))
(defn rolling-window
  "Creates a transducer that generates rolling windows of a specified size.
   Arguments:
   - window-size: The number of elements in each window
   Returns a transducer that transforms a sequence into overlapping windows.
   Example:
   (into [] (rolling-window 3) [1 2 3 4 5])
   ;; Returns [[1 2 3] [2 3 4] [3 4 5]]"
  [window-size]
  (fn [rf]
    (let [window (volatile! (clojure.lang.PersistentQueue/EMPTY))]
      (fn
        ([] (rf))
        ([result] (rf result))
        ([result input]
         (vswap! window conj input)
         (if (= (count @window) window-size)
           (let [window-result (vec @window)]
             (vswap! window pop)
             (rf result window-result))
           result))))))

; Decision-making transducer
(defn decision-transducer []
  (comp
    (map (fn [window]
           (let [sum (reduce + window)]
             (cond
               (> sum 0) :buy
               (< sum 0) :sell
               :else :hold))))
    (map-indexed vector)))

(defn buy-sell-transducer []
  (comp (map (fn [[prev curr]]
               (let [direction (- curr prev)]
                 direction
                 #_(cond
                     (> direction 0) :buy
                     (< direction 0) :sell
                     :else :hold))))))

(def sign-eval-transducer
  (comp
    (rolling-window 2)
    (map (fn [[prev curr]]
           (let [diff (> curr prev)]
             (case diff
               true :up false :down))))
    (rolling-window 2)
    (map (fn [direction]
           (case direction
             [:up :up] [direction :sell]
             [:down :down] [direction :buy]
             [direction :hold])))))

(comment
  (def prices [10 11 12 12 11 10 9])
  (def prices [10 15 13 12 11 10 9 11 16 29])
  (sequence sign-eval-transducer prices)
  (transduce sign-eval-transducer conj prices)
  (into [] sign-eval-transducer prices)

  *e)

;; Introducing async transducing
(comment
  (defn apply-transducer [in xf]
    (let [out          (async/chan 8)
          thread-count 1]
      (async/pipeline thread-count out xf in)
      out))

  (def in (async/chan))
  (def out (apply-transducer in (map inc)))

  (async/put! in 3)
  (async/take! out (fn [x] (println x)))
  )

;; Async processing strategy
(comment
  (def prices-in (async/chan))
  (def signals-out (apply-transducer in (sign-eval-transducer)))

  (async/put! prices-in 1)
  (async/put! prices-in 2)
  (async/put! prices-in 3)
  (async/put! prices-in 4)
  (async/put! prices-in 5)

  (async/take! signals-out (fn [x] (println x)))
  )

;; Process trading strategy
(defn process-trading-strategy [price-changes]
  (let [in-chan  (async/chan 10 (rolling-window 3))
        out-chan (async/chan 10 (decision-transducer))]
    (async/onto-chan! in-chan price-changes)
    (async/pipeline 1 out-chan decision-transducer in-chan)
    (loop [result []]
      (if-let [decision (async/<!! out-chan)]
        (recur (conj result decision))
        result))))

(comment
  (def gold-price-dataset (price/history))
  (process-trading-strategy gold-price-dataset)

  )
