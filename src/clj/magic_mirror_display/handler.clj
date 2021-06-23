(ns magic-mirror-display.handler
  (:require
   [reitit.ring :as reitit-ring]
   [magic-mirror-display.middleware :refer [middleware]]
   [hiccup.page :refer [include-js include-css html5]]
   [config.core :refer [env]]
   [clojure.data.json :as json]
   [clj-http.client :as client]
   [clojure.string :as str]))

(def mount-target
  [:div#app
   [:h2 "Welcome to magic-mirror-display"]
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

(def app
  (reitit-ring/ring-handler
   (reitit-ring/router
    [["/" {:get {:handler index-handler}}]
     ["/items"
      ["" {:get {:handler index-handler}}]
      ["/:item-id" {:get {:handler index-handler
                          :parameters {:path {:item-id int?}}}}]]
     ["/about" {:get {:handler index-handler}}]
     ["/weather" {:get {:handler weather-handler}}]])
   (reitit-ring/routes
    (reitit-ring/create-resource-handler {:path "/" :root "/public"})
    (reitit-ring/create-default-handler))
   {:middleware middleware}))
