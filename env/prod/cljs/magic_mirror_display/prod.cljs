(ns magic-mirror-display.prod
  (:require [magic-mirror-display.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
