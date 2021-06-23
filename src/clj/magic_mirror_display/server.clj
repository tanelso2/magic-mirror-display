(ns magic-mirror-display.server
    (:require
     [magic-mirror-display.handler :refer [app]]
     [config.core :refer [env]]
     [ring.adapter.jetty :refer [run-jetty]])
    (:gen-class))

(defn -main [& args]
  (let [port (or (env :port) 8080)]
    (run-jetty #'app {:port port :join? false})))
