(ns magic-mirror-display.oauth
  (:require
   [clj-http.client :as client]
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [magic-mirror-display.util :as util]
   [ring.util.response :refer [redirect]]
   [clojure.tools.trace :as trace])
  (:import
   [java.util Date]))

(defn get-creds-from-file
  [f]
  (-> f
      (slurp)
      (json/read-str :key-fn keyword)))

(defn basic-auth-header
  [creds-file]
  (let [{:keys [id secret]} (get-creds-from-file creds-file)
        auth-str (str id ":" secret)]
    (str "Basic " auth-str)))

(defn basic-b64-auth-header
  [creds-file]
  (let [{:keys [id secret]} (get-creds-from-file creds-file)
        auth-str (str id ":" secret)
        encoded-auth (util/base64-encode auth-str)];)]
    (str "Basic " encoded-auth)))

(defn reddit-auth-header
  [creds-file]
  (let [{:keys [id secret]} (get-creds-from-file creds-file)
        auth-str (str id ":")]
    (str "Basic " auth-str)))

  

; oauth flow customization ...
; redirect url
; oauth start path?
; auth token file
; check for refresh
; url to sign in to


(defn get-standard-oauth-config
  [{:keys [name access-token-url authorize-url scopes authorize-extra-body extra-headers]}]
  (let [access-token-file (str "." name "-access-token.edn")
        redirect-uri (str "http://localhost:3000/" name "_oauth_finish")
        creds-file (str name "-creds.json")
        auth-header-fn (fn [] (basic-b64-auth-header creds-file))]
    {:access-token-file access-token-file
     :redirect-uri redirect-uri
     :creds-file creds-file
     :auth-header-fn auth-header-fn
     :access-token-url access-token-url
     :authorize-url authorize-url 
     :scopes scopes
     :scope-str (str/join " " scopes)
     :oauth-start-path (str "/" name "_oauth_start")
     :authorize-extra-body authorize-extra-body
     :oauth-finish-path (str "/" name "_oauth_finish")
     :extra-headers extra-headers}))


  

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

(def ^:dynamic *access-token-file* ".spotify-access-token.edn")

(defn write-access-token-file!
  ([x] (write-access-token-file! {:access-token-file *access-token-file*} x))
  ([m x]
   (let [{:keys [access-token-file]} m]
     (spit access-token-file (pr-str x)))))

(defn fetch-new-access-token!
  [m code]
  (let [{:keys [access-token-url 
                redirect-uri 
                auth-header-fn
                extra-headers]} m
        headers (merge 
                  {"Content-Type" "application/x-www-form-urlencoded"
                   "Authorization" (auth-header-fn)}
                  extra-headers)
        body {"grant_type" "authorization_code"
              "code" code
              "redirect_uri" redirect-uri}
        resp (trace/trace 
               (client/post access-token-url 
                {:headers headers
                 :form-params body
                 :accept :json}))
        body-str (:body resp)
        body (json/read-str body-str :key-fn keyword)]
    (write-access-token-file! m body)
    (get body :access_token)))
  

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
  ([] (needs-refresh? {:access-token-file *access-token-file*}))
  ([m]
   (let [{:keys [access-token-file]} m
         now (.getTime (new Date))
         expiration-time (-> access-token-file
                              (util/file-last-modified)
                              ; Hard code to 60 minutes for now
                              (+ (* 60 60 1000)))]
     (>= now expiration-time))))

(defn read-access-token-file
  ([]
   (read-access-token-file {:access-token-file *access-token-file*}))
  ([m] (let [{:keys [access-token-file]} m]
         (-> access-token-file
             (slurp)
             (edn/read-string)))))

(defn refresh-token!
  ([]
   (let [x (read-access-token-file)
         refresh-token (get x :refresh_token)
         url "https://accounts.spotify.com/api/token"
         headers {"Content-Type" "application/x-www-form-url-encoded"
                  "Authorization" (get-spotify-auth-header)
                  "User-Agent" "yassss"}
         body {"grant_type" "refresh_token"
               "refresh_token" refresh-token}
         resp (client/post url {:headers headers
                                :form-params body})
         body-str (:body resp)
         body (json/read-str body-str :key-fn keyword)
         new-x (merge x body)]
     (write-access-token-file! new-x)
     (:access_token new-x)))
  ([m]
   (println "Refreshing token!")
   (let [{:keys [extra-headers access-token-url]} m
         x (read-access-token-file m)
         refresh-token (get x :refresh_token)
         headers (merge 
                   {"Content-Type" "application/x-www-form-url-encoded"
                    "Authorization" ((:auth-header-fn m))}
                   extra-headers)
         body {"grant_type" "refresh_token"
               "refresh_token" refresh-token}
         resp (client/post access-token-url {:headers headers
                                             :form-params body})
         body-str (:body resp)
         body (json/read-str body-str :key-fn keyword)
         new-x (merge x body)]
     (write-access-token-file! m new-x)
     (:access_token new-x))))

(defn get-access-token!
  "Gets the access token to access spotify. May refresh the token if needed.

  Throws an exception if unable to get an access token"
  ([] (get-access-token! (get-standard-oauth-config "default")))
  ([m]
   (let [{:keys [access-token-file]} m]
     (if (util/file-exists? access-token-file)
       (let [x (read-access-token-file m)
             access-token (get x :access_token)
             refresh-token (get x :refresh_token)]
         (if (needs-refresh? m)
           (refresh-token! m)
           access-token))
       (throw (Exception. "No access token file found, wtf dude"))))))

(defn oauth-start-handler [m _req]
 (let [{:keys [id secret]} (get-creds-from-file (:creds-file m))
       url (:authorize-url m)
       default-query-params {"client_id" id
                             "response_type" "code"
                             "redirect_uri" (:redirect-uri m)
                             "scope" (:scope-str m)}
       extra-query-params (:authorize-extra-body m)
       query-params (merge default-query-params extra-query-params)
       full-url (util/unparse-url url query-params)]
    (redirect full-url)))

(defn oauth-finish-handler [m request]
  (let [code (get-in request [:query-params "code"])
        _ (fetch-new-access-token! m code)]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body "Oauth flow complete, you can close this tab"}))
