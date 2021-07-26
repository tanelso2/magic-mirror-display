(ns magic-mirror-display.spotify
  (:require
   [clj-http.client :as client]
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [magic-mirror-display.util :as util])
  (:import
   [java.util Date]))

(defn get-spotify-creds [] 
  (-> "spotify-creds.json"
      (slurp)
      (json/read-str :key-fn keyword)))

(def spotify-scopes
  (str/join " "
    ["user-read-playback-state"
     "user-read-currently-playing"]))

(def redirect-uri "http://localhost:3000/interact")

(defn get-spotify-auth-header
  []
  (let [{:keys [id secret]} (get-spotify-creds)
        auth-str (str id ":" secret)
        encoded-auth (util/base64-encode auth-str)]
    (str "Basic " encoded-auth)))

(def access-token-file ".spotify-access-token.edn")

(defn write-access-token-file!
  [x]
  (spit access-token-file (pr-str x)))

(defn fetch-new-spotify-access-token!
  [code]
  (let [
        url "https://accounts.spotify.com/api/token"
        headers {"Content-Type" "application/x-www-form-url-encoded"
                 "Authorization" (get-spotify-auth-header)}
        body {"grant_type" "authorization_code"
              "code" code
              "redirect_uri" redirect-uri}
        resp (client/post url {:headers headers
                               :form-params body})
        body-str (:body resp)
        body (json/read-str body-str :key-fn keyword)]
    (write-access-token-file! body)
    (get body :access_token)))

(defn needs-refresh?
  []
  (let [now (.getTime (new Date))
        expiration-time (-> access-token-file
                            (util/file-last-modified)
                            ; Hard code to 60 minutes for now
                            (+ (* 60 60 1000)))]
   (>= now expiration-time)))

(defn read-access-token-file
  []
  (-> access-token-file
      (slurp)
      (edn/read-string)))

(defn refresh-token!
  []
  (let [x (read-access-token-file)
        refresh-token (get x :refresh_token)
        url "https://accounts.spotify.com/api/token"
        headers {"Content-Type" "application/x-www-form-url-encoded"
                 "Authorization" (get-spotify-auth-header)}
        body {"grant_type" "refresh_token"
              "refresh_token" refresh-token}
        resp (client/post url {:headers headers
                               :form-params body})
        body-str (:body resp)
        body (json/read-str body-str :key-fn keyword)
        new-x (merge x body)]
    (write-access-token-file! new-x)
    (:access_token new-x)))

(defn get-spotify-access-token!
  "Gets the access token to access spotify. May refresh the token if needed.

  Throws an exception if unable to get an access token"
  []
  (if (util/file-exists? access-token-file)
    (let [x (read-access-token-file)
          access-token (get x :access_token)
          refresh-token (get x :refresh_token)]
      (if (needs-refresh?)
        (refresh-token!)
        access-token))
    (throw "No access token file found, wtf dude")))

(defn get-currently-playing-info
  []
  (let [token (get-spotify-access-token!)
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
