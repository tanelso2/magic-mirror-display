(ns magic-mirror-display.spotify
  (:require
   [clj-http.client :as client]
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [magic-mirror-display.util :as util]
   [magic-mirror-display.oauth :as oauth])
  (:import
   [java.util Date]))

(def spotify-scopes
  ["user-read-playback-state"
   "user-read-currently-playing"])

(def spotify-oauth-config 
  (oauth/get-standard-oauth-config 
    {:name "spotify"
     :access-token-url "https://accounts.spotify.com/api/token"
     :authorize-url "https://accounts.spotify.com/authorize"
     :scopes spotify-scopes}))


(defn get-currently-playing-info
  []
  (let [token (oauth/get-access-token! spotify-oauth-config)
        auth-header (str "Bearer " token)
        url "https://api.spotify.com/v1/me/player/currently-playing"
        resp (client/get url {:query-params {"market" "from_token"}
                              :headers {"Authorization" auth-header}
                              :accept :json})
        status (:status resp)]
    (case status
      204 :not-playing
      200 (-> resp
              (:body)))))
              ;(json/read-str :key-fn keyword)))))
