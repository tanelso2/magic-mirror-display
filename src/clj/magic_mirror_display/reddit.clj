(ns magic-mirror-display.reddit
  (:require
    [magic-mirror-display.oauth :as oauth]
    [clj-http.client :as client]
    [clojure.data.json :as json]))

(def reddit-scopes
  ["identity" "read" "history" "save" "mysubreddits"])
   

(def reddit-oauth-config
  (merge
    (oauth/get-standard-oauth-config
      {:name "reddit"
       :access-token-url "https://www.reddit.com/api/v1/access_token"
       :authorize-url "https://www.reddit.com/api/v1/authorize"
       :authorize-extra-body {"state" "12345"
                              "duration" "permanent"}
       :scopes reddit-scopes
       :extra-headers {"User-Agent" "saved-links-randomizer 1.0 by /u/speedster217"}})
    {:username "speedster217"}))

(def fetch-saved-links
 (memoize
  (fn
    ([] (fetch-saved-links nil))
    (
      [after]
      (let [access-token (oauth/get-access-token! reddit-oauth-config)
            {:keys [username]} reddit-oauth-config
            url (str "https://oauth.reddit.com/user/" username "/saved")
            resp (client/get url
                             {:accept :json
                              :headers {"User-Agent" "saved-links-randomizer 1.0 by /u/speedster217"
                                        "Authorization" (str "Bearer " access-token)}
                              :query-params {"type" "links"
                                             "limit" "100"
                                             "show" "given"
                                             "sort" "new"
                                             "after" after}})

            body-str (:body resp)
            body (json/read-str body-str :key-fn keyword)
            next-after (get-in body [:data :after])
            children (get-in body [:data :children])]
        (if (some? next-after)
          (concat children (fetch-saved-links next-after))
          children))))))
      


