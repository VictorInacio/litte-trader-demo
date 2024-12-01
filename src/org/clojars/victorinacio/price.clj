(ns org.clojars.victorinacio.price
  (:require [tablecloth.api :as tc]
            [tech.v3.dataset :as ds]))

;; Load historical data

;; Get last x years of asset price

(defn history [file-path]
  (let [dataset (ds/->dataset file-path {:key-fn keyword})]
      (tc/dataset dataset)))
