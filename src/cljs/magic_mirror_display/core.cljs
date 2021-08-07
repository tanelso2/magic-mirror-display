(ns magic-mirror-display.core
  (:require
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]
   [reagent.session :as session]
   [reitit.frontend :as reitit]
   [clerk.core :as clerk]
   [clojure.string :as str]
   [accountant.core :as accountant]
   [cljs-http.client :as http]
   [cljs.core.async :refer [<! go]]))

;; -------------------------
;; Routes

(def router
  (reitit/router
   [["/" :index]
    ["/items"
     ["" :items]
     ["/:item-id" :item]]
    ["/about" :about]
    ["/reddit" :reddit]]))

(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))

;; -------------------------
;; State

(def music-state (atom nil))

(def weather-state (atom nil))

(def timer (atom (js/Date.)))

(def reddit-state (atom nil))

(defonce time-updater (js/setInterval
                        #(reset! timer (js/Date.)) 1000))

(defn fetch-weather []
  (go (let [resp (<! (http/get "/weather"))
            body (:body resp)]
        (reset! weather-state body))))

(defonce weather-updater (js/setInterval #(fetch-weather) 5000))

(defn fetch-now-playing []
  (go (let [resp (<! (http/get "/now-playing"))
            status (:status resp)
            new-state (case status
                        204 :not-playing
                        200 (-> resp
                               (:body))
                        :error)]
        (reset! music-state new-state))))

(defonce now-playing-updater (js/setInterval #(fetch-now-playing) 5000))

(defn fetch-saved-link! []
  (go (let [resp (<! (http/get "/saved/random"))
            body (-> resp
                     :body)]
        (reset! reddit-state body))))

;; -------------------------
;; Page components

(defn clock []
  (let [time-str (-> @timer
                     .toTimeString
                     (str/split " ")
                     first)
        date-str (-> @timer
                     .toDateString)]
    [:div
      [:div time-str]
      [:div date-str]]))

(defn artist-display
  [artists]
  ; deref music-state so reagent knows when to update it
  (let [_ @music-state]
    (if (= 1 (count artists))
      [:h2 "Artist: " (get-in artists [0 :name])]
      (let [artist-names (map :name artists)
            names (str/join ",  " artist-names)]
        [:h2 "Artists: " names]))))

(defn music-display []
  (fn []
    (let [music @music-state]
      (if (nil? music)
        [:span [:p "Loading music..."]]
        (let [track (:item music)
              track-name (:name track)
              artists (:artists track)]
          [:span.main
           [:marquee [:h1 "NOW PLAYING"]]
           [:h2 "Track: " track-name]
           [artist-display artists]])))))

(defn is-image-link? [url]
  ; TODO: Remove query params and fragments from url
  (let [suf #(str/ends-with? url %)
        pre #(str/starts-with? url %)
        con #(str/includes? url %)]
    (or (suf ".jpg") 
        (suf ".jpeg")
        (suf ".gif")
        (suf ".png")
        (suf ".gifv") ; not sure if this will work
        (con "gyfcat.com"))))

(defn random-link-display []
  (fn []
    (let [saved-link @reddit-state]
      [:span
        [:button {:on-click #(fetch-saved-link!)} "ANOTHER"]
        (if (nil? saved-link)
          (let [_ (fetch-saved-link!)] 
            [:span [:p "loading reddit data...."]])
          (let [{:keys [data kind]} saved-link]
            [:span
              (case kind
                "t1" [:span "Comments not implemented yet"]
                "t3" (let [{:keys [url]} data] 
                      [:span 
                        [:p "t3"]
                        [:p (str "Url is " url)]
                        (if (is-image-link? url)
                          [:img {:src url}])]))]))])))

    

(defn weather-display []
  (fn []
    (let [weather @weather-state]
      (if (nil? weather)
        [:span [:p "Loading weather...."]]
        (let [icon (get-in weather [:weather 0 :icon])
              iconurl (str "https://openweathermap.org/img/w/" icon ".png")
              curr-temp (get-in weather [:main :temp])
              max-temp (get-in weather [:main :temp_max])
              min-temp (get-in weather [:main :temp_min])]
          [:span
            [:img {:src iconurl}]
            [:h2 "Current " curr-temp " °F"]
            [:h2 "Max " max-temp " °F"]
            [:h2 "Min " min-temp " °F"]])))))

(defn current-task-link []
  (fn []
    [:span
     [:a {:href "/reddit_oauth_start"} "REDDIT OAUTH QUICKLINK"]]))

(defn home-page []
  (fn []
    [:span.main
     [current-task-link]
     [clock]
     [music-display]
     [weather-display]]))


(defn reddit-page []
  (fn []
    [:span.main
     [random-link-display]]))


;; -------------------------
;; Translate routes -> page components 
(defn page-for [route]
  (case route
    :index #'home-page
    :reddit #'reddit-page))


;; -------------------------
;; Page mounting component

(defn current-page []
  (fn []
    (let [page (:current-page (session/get :route))]
      [:div
       ;; [:header
       ;;  [:p [:a {:href (path-for :index)} "Home"] " | "
       ;;   [:a {:href (path-for :about)} "About magic-mirror-display"]]]
       [page]])))
       ;; [:footer
       ;;  [:p "magic-mirror-display was generated by the "
       ;;   [:a {:href "https://github.com/reagent-project/reagent-template"} "Reagent Template"] "."]]])))

;; -------------------------
;; Initialize app

(defn mount-root []
  (rdom/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (clerk/initialize!)
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (let [match (reitit/match-by-path router path)
            current-page (:name (:data  match))
            route-params (:path-params match)]
        (reagent/after-render clerk/after-render!)
        (session/put! :route {:current-page (page-for current-page)
                              :route-params route-params})
        (clerk/navigate-page! path)
        ))
    :path-exists?
    (fn [path]
      (boolean (reitit/match-by-path router path)))})
  (accountant/dispatch-current!)
  (mount-root))
