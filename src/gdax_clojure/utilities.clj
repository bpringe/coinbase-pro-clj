(ns gdax-clojure.utilities
  (:require
    [clojure.data.json :as json]
    [ring.util.codec :refer [form-encode]]))

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

(defn parse-request-path
  [request-url]
  (second (clojure.string/split request-url #".com")))

(defn build-base-request
  [method url]
  {:method method
   :url url
   :accept :json
   :as :json})

(defn build-get-request
  [url]
  (build-base-request "GET" url))

(defn build-post-request
  ([url body]
   (build-post-request url body {}))
  ([url body opts]
   (merge (build-base-request "POST" url)
          {:body (edn->json body)
           :content-type :json}
          opts)))

(defn build-delete-request
  ([url]
   (build-delete-request url {}))
  ([url opts]
   (merge (build-base-request "DELETE" url)
          opts)))

(defn append-query-params
  [query-params request]
  (if (empty? query-params)
    request
    (update-in request [:url] 
      #(str % 
        (if (clojure.string/includes? % "?") "&" "?") 
        (form-encode query-params)))))

