(ns magic-mirror-display.handler
  (:require
   [clj-http.client :as client]
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [config.core :refer [env]]
   [hiccup.page :refer [include-js include-css html5]]
   [magic-mirror-display.middleware :refer [middleware]]
   [magic-mirror-display.oauth :as oauth]
   [magic-mirror-display.reddit :as reddit]
   [magic-mirror-display.spotify :as spotify]
   [magic-mirror-display.util :as util]
   [reitit.ring :as reitit-ring]
   [ring.util.response :refer [redirect]])
  (:import
   [java.util Base64]
   [java.util Date]))

(def mount-target
  [:div#app
   [:h2 "Loading..."]
   [:p "please wait while Figwheel/shadow-cljs is waking up ..."]
   [:p "(Check the js console for hints if nothing exciting happens.)"]])

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-css (if (env :dev) "/css/site.css" "/css/site.min.css"))])

(defn loading-page []
  (html5
   (head)
   [:body {:class "body-container"}
    mount-target
    (include-js "/js/app.js")
    [:script "magic_mirror_display.core.init_BANG_()"]]))

(defn get-weather []
  (let [url "https://api.openweathermap.org/data/2.5/weather"
        zipcode "63105"
        apikey (str/trim (slurp "openweatherapikey.txt"))
        resp (client/get url {:query-params {"appid" apikey
                                             "zip" zipcode
                                             "units" "imperial"}
                              :accept :json})]
    (:body resp)))


(defn weather-handler
  [_request]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (get-weather)})

(defn index-handler
  [_request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (loading-page)})

(defn oauth-page [req]
  (html5
   (head)
   [:body {:class "body-container"}
     "Successfully got a token! You can now close this tab"]))

(defn now-playing-handler
  [_req]
  (let [now-playing (spotify/get-currently-playing-info)]
    (if (= now-playing :not-playing)
      {:status 204
       :body ""}
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body now-playing})))

(defn random-saved-link-handler
  [_req]
  (let [saved-links (reddit/fetch-saved-links)]
    (if (empty? saved-links)
      {:status 204
       :body ""}
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (json/write-str (rand-nth saved-links))})))


(def app
  (let [spotify spotify/spotify-oauth-config]
    (reitit-ring/ring-handler
     (reitit-ring/router
      [["/" {:get {:handler index-handler}}]
       ["/reddit" {:get {:handler index-handler}}]
       ["/weather" {:get {:handler weather-handler}}]
       ["/now-playing" {:get {:handler now-playing-handler}}]
       ["/saved/random" {:get {:handler random-saved-link-handler}}]
       [(:oauth-start-path spotify) {:get {:handler (fn [req] (oauth/oauth-start-handler spotify req))}}]
       [(:oauth-finish-path spotify) {:get {:handler (fn [req] (oauth/oauth-finish-handler spotify req))}}]
       [(:oauth-start-path reddit/reddit-oauth-config) {:get {:handler (fn [req] (oauth/oauth-start-handler reddit/reddit-oauth-config req))}}]
       [(:oauth-finish-path reddit/reddit-oauth-config) {:get {:handler (fn [req] (oauth/oauth-finish-handler reddit/reddit-oauth-config req))}}]])
     (reitit-ring/routes
      (reitit-ring/create-resource-handler {:path "/" :root "/public"})
      (reitit-ring/create-default-handler))
     {:middleware middleware})))
