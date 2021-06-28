(ns magic-mirror-display.util
  (:require
    [clojure.string :as str]
    [clojure.java.io :as io]
    [clj-http.client :as client])
  (:import
    [java.util Base64]
    [java.util Date]))

(defn unparse-url
  [url query-params]
  (let [query-str (client/generate-query-string query-params)]
    (str url "?" query-str)))

(defn base64-encode
  [s]
  (.encodeToString (Base64/getEncoder) (.getBytes s)))

(defn file-exists?
  [filename]
  (.exists (io/file filename)))

(defn file-last-modified
  [filename]
  (.lastModified (io/file filename)))
