(ns gdax-clj.utilities
  (:require
    [clojure.data.json :as json]))

(defn edn->json
  [edn-content]
  (json/write-str edn-content))

(defn json->edn
  [json-content]
  (json/read-str json-content :key-fn keyword))

(defn contains-many? [m & ks]
  (every? #(contains? m %) ks))

(defn get-timestamp
  []
  (quot (System/currentTimeMillis) 1000))

(defn build-base-request
  [method url]
  {:method method
   :url url
   :accept :json
   :as :json})

(defn build-get-request
  [url & opts]
  (merge (build-base-request "GET" url)
         opts))

(defn build-post-request
  [url body & opts]
  (merge (build-base-request "POST" url)
         {:body (edn->json body)
          :content-type :json}
         opts))

(defn build-delete-request
  [url & opts]
  (merge (build-base-request "DELETE" url)
         opts))

(defn map->query-string
  [params]
  (clojure.string/join "&"
    (for [[k v] params]
      (str (name k) "=" (java.net.URLEncoder/encode (str v))))))

(defn append-query-params
  [query-params request]
  (if (empty? query-params)
    request
    (update-in request [:url] 
      #(str % 
        (if (clojure.string/includes? % "?") "&" "?") 
        (map->query-string query-params)))))
