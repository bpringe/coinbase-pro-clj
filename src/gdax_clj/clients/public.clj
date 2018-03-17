(ns gdax-clj.clients.public
  (:require 
    [gdax-clj.core :as gdax]
    [clj-http.client :as http]
    [clj-time.core :as t]
    [clojure.data.json :as json]))

;; ## Request building utilities

(defn- build-base-request
  [method url]
  {:method method
   :url url
   :accept :json
   :as :json})

(defn- build-get-request
  [url & [options]]
  (merge (build-base-request "GET" url)
         options))

(defn- build-post-request
  [url body & [options]]
  (merge (build-base-request "POST" url)
         {:body (json/write-str body)
          :content-type :json}
         options))

(defn- build-delete-request
  [url & [options]]
  (merge (build-base-request "DELETE" url)
         options))

(defn- map->query-string
  [params]
  (clojure.string/join "&"
    (for [[k v] params]
      (str (name k) "=" (java.net.URLEncoder/encode (str v))))))

(defn- append-query-params
  [query-params request]
  (if (empty? query-params)
    request
    (update-in request [:url] 
      #(str % 
        (if (clojure.string/includes? % "?") "&" "?") 
        (map->query-string query-params)))))

;; ## PublicClient Type

;; - `api-url`
;; The base url for the GDAX API endpoints

(defrecord PublicClient
  [api-url]
  
  gdax-clj/GdaxPublicEndpoints

  (get-time
    [this]
    (http/request (build-get-request (str api-url "/time"))))
  
  (get-products
    [this]
    (http/request (build-get-request (str api-url "/products"))))
  
  (get-order-book
    [this product-id level]
    (->> (str api-url "/products/" product-id "/book?level=" level)
        build-get-request
        http/request))

  (get-order-book
    [this product-id]
    (gdax/get-order-book this product-id 1))
    
  (get-ticker
    [this product-id]
    (->> (str api-url "/products/" product-id "/ticker")
       build-get-request
       http/request))
  
  (get-trades
    [this product-id]
    (->> (str api-url "/products/" product-id "/trades")
       build-get-request
       http/request))
       
  (get-historic-rates
    [this product-id]
    (->> (str api-url "/products/eth-usd/candles")
      build-get-request
      http/request))
      
  (get-historic-rates
    [this product-id start end granularity]
    (->> (str api-url "/products/" product-id "/candles?start="
              start "&end=" end "&granularity=" granularity)
         build-get-request
         http/request))
         
  (get-product-stats
    [this product-id]
    (->> (str api-url "/products/" product-id "/stats")
       build-get-request
       http/request))
       
  (get-currencies
    [this]
    (http/request (build-get-request (str api-url "/currencies")))))
    
;; https://api-public.sandbox.gdax.com
;; https://api.gdax.com
(def public-client (->PublicClient "https://api.gdax.com"))

(clojure.pprint/pprint (gdax/get-currencies public-client))
                                                
