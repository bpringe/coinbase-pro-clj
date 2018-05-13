(ns gdax-clj.core
  "Core protocols for interacting with the GDAX API"
  (:require 
    [pandect.algo.sha256 :refer :all]
    [cheshire.core :refer :all]
    [clj-http.client :as http]
    [environ.core :refer [env]]
    [clj-time.core :as t]
    [clojure.data.codec.base64 :as b64]
    [clojure.data.json :as json]
    [clojure.pprint :refer [pprint]]
    [gniazdo.core :as ws])
  (:import (org.eclipse.jetty.websocket.client WebSocketClient)
           (org.eclipse.jetty.util.ssl SslContextFactory)))

;; ## Utilities

(defn- edn->json
  [edn-content]
  (json/write-str edn-content))

(defn- json->edn
  [json-content]
  (json/read-str json-content :key-fn keyword))

(defn- contains-many? [m & ks]
  (every? #(contains? m %) ks))

(defn- get-timestamp
  []
  (quot (System/currentTimeMillis) 1000))

(defn- build-base-request
  [method url]
  {:method method
   :url url
   :accept :json
   :as :json})

(defn- build-get-request
  [url & opts]
  (merge (build-base-request "GET" url)
         opts))

(defn- build-post-request
  [url body & opts]
  (merge (build-base-request "POST" url)
         {:body (edn->json body)
          :content-type :json}
         opts))

(defn- build-delete-request
  [url & opts]
  (merge (build-base-request "DELETE" url)
         opts))

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

;; ## Configuration

(def granularities {:1m 60
                    :5m 300
                    :15m 900
                    :1h 3600
                    :6h 21600
                    :1d 86400})
(def rest-url "https://api.gdax.com")
(def websocket-url "wss://ws-feed.gdax.com")
(def sandbox-rest-url "https://public.sandbox.gdax.com")
(def sandbox-websocket-url "wss://ws-feed-public.sandbox.gdax.com")

;; ## Public endpoints

;; - `client` will take the following shape
;; {:url
;;  :key
;;  :secret
;;  :passphrase}

(defn get-time
  [client]
  (http/request (build-get-request (str (:url client) "/time"))))

(defn get-products
  [client]
  (http/request (build-get-request (str (:url client) "/products"))))

(defn get-order-book
  ([client product-id]
   (get-order-book client product-id 1))
  ([client product-id level]
   (->> (str (:url client) "/products/" product-id "/book?level=" level)
        build-get-request
        http/request)))
  
(defn get-ticker
  [client product-id]
  (->> (str (:url client) "/products/" product-id "/ticker")
       build-get-request
       http/request))

(defn get-trades
  [client product-id]
  (->> (str (:url client) "/products/" product-id "/trades")
       build-get-request
       http/request))
    
(defn get-historic-rates
  ([client product-id]
   (->> (str (:url client) "/products/" product-id "/stats")
        build-get-request
        http/request))
  ([client product-id start end granularity]
   (->> (str (:url client) "/products/" product-id "/candles?start="
           start "&end=" end "&granularity=" granularity)
        build-get-request
        http/request)))
       
(defn get-product-stats
  [client product-id]
  (->> (str (:url client) "/products/" product-id "/stats")
       build-get-request
       http/request))
     
(defn get-currencies
  [client]
  (http/request (build-get-request (str (:url client) "/currencies"))))

;; ## Authentication

(defn- parse-request-path
  [request-url]
  (second (clojure.string/split request-url #".com")))

(defn- create-prehash-string
  [timestamp request]
  (str timestamp (clojure.string/upper-case (:method request)) 
    (parse-request-path (:url request)) (:body request)))
                    
(defn- create-http-signature
  [secret timestamp request]
  (let [secret-decoded (b64/decode (.getBytes secret))
        prehash-string (create-prehash-string timestamp request)
        hmac (sha256-hmac* prehash-string secret-decoded)]
    (-> hmac
        b64/encode
        String.)))

(defn- create-websocket-signature
  [secret timestamp]
  (let [secret-decoded (b64/decode (.getBytes secret))
        prehash-string (str timestamp "GET/users/self/verify")
        hmac (sha256-hmac* prehash-string secret-decoded)]
    (-> hmac
        b64/encode
        String.)))

(defn- sign-request 
  [client request]
  (let [timestamp (quot (System/currentTimeMillis) 1000)]
    (update-in request [:headers] merge {"CB-ACCESS-KEY" (:key client)
                                         "CB-ACCESS-SIGN" (create-http-signature (:secret client) timestamp request)
                                         "CB-ACCESS-TIMESTAMP" timestamp
                                         "CB-ACCESS-PASSPHRASE" (:passphrase client)})))

(defn- sign-message
  [message {:keys [key secret passphrase]}]
  (let [timestamp (get-timestamp)]
    (merge message
           {:key key
            :passphrase passphrase
            :timestamp timestamp
            :signature (create-websocket-signature secret timestamp)})))

;; ## Private endpoints

(defn get-accounts 
  [client]
  (->> (build-get-request (str (:url client) "/accounts"))
       (sign-request client)
       http/request))

(defn get-account-by-id
  [client account-id]
  (->> (build-get-request (str (:url client) "/accounts/" account-id))
       (sign-request client)
       http/request))

(defn get-account-history
  [client account-id & paging-opts]
  (->> (build-get-request (str (:url client) "/accounts/" account-id "/ledger"))
       (append-query-params paging-opts)
       (sign-request client)
       http/request))

(defn get-account-holds
  [client account-id & paging-opts]
  (->> (build-get-request (str (:url client) "/accounts/" account-id "/holds"))
       (append-query-params paging-opts)
       (sign-request client)
       http/request))

(defn place-order
  [client side product-id & opts]
  (let [body (merge opts {:side side
                             :product_id (clojure.string/upper-case product-id)})]
    (->> (build-post-request (str (:url client) "/orders") body)
         (sign-request client)
         http/request)))

(defn place-limit-order
  [client side product-id price size & opts]
  (place-order client side product-id (merge opts {:price price}
                                                  :size size
                                                      :type "limit")))

(defn place-market-order
  [client side product-id & opts]
  (place-order client side product-id (merge opts {:type "market"})))

(defn place-stop-order
  [client side product-id price & opts]
  (place-order client side product-id (merge opts {:type "stop"}
                                               :price price)))

(defn get-orders
  [client & {:keys [statuses] :as opts}]
  (let [query-string (clojure.string/join "&" (map #(str "status=" (name %)) statuses))
        rest-opts (dissoc opts :statuses)]
    (->> (build-get-request (str (:url client)
                                 "/orders"
                                 (when-not (clojure.string/blank? query-string) "?")
                                 query-string))
         (append-query-params rest-opts)
         (sign-request client)
         http/request)))

(defn cancel-order
  [client order-id]
  (->> (build-delete-request (str (:url client) "/orders/" order-id))
       (sign-request client)
       http/request))

(defn cancel-all
  [client & product-id]
  (->> (build-delete-request 
          (str (:url client) "/orders" (when-not (nil? product-id) (str "?product_id=" product-id))))
       (sign-request client)
       http/request))

(defn get-order
  [client order-id]
  (->> (build-get-request (str (:url client) "/orders/" order-id))
       (sign-request client)
       http/request))

(defn get-fills
  [client & opts]
  (->> (build-get-request (str (:url client) "/fills"))
       (append-query-params opts)
       (sign-request client)
       http/request))

(defn get-payment-methods
  [client]
  (->> (build-get-request (str (:url client) "/payment-methods"))
       (sign-request client)
       http/request))

(defn get-coinbase-accounts
  [client]
  (->> (build-get-request (str (:url client) "/coinbase-accounts"))
       (sign-request client)
       http/request))

(defn deposit-from-coinbase
  [client amount currency coinbase-account-id]
  (->> (build-post-request 
         (str (:url client) "/deposits/coinbase-account") 
         {:amount amount
          :currency (clojure.string/upper-case currency)
          :coinbase_account_id coinbase-account-id})
       (sign-request client)
       http/request))

(defn withdraw-to-coinbase
  [client amount currency coinbase-account-id]
  (->> (build-post-request 
         (str (:url client) "/withdrawals/coinbase-account")
         {:amount amount
          :currency (clojure.string/upper-case currency)
          :coinbase_account_id coinbase-account-id})
       (sign-request client)
       http/request))

;; TODO: try this with actual API instead of sandbax. If it works, also implement
;; deposit and withdraw for payment methods too
(defn withdraw-to-crypto-address
  [client amount currency crypto-address]
  (->> (build-post-request
         (str (:url client) "/withdrawals/crypto")
         {:amount amount
          :currency (clojure.string/upper-case currency)
          :crypto_address crypto-address})
       (sign-request client)
       http/request))

(defn generate-fills-report
  [client start-date end-date product-id & opts]
  (let [params (merge opts
                      {:type "fills"
                       :start_date start-date
                       :end_date end-date
                       :product_id (clojure.string/upper-case product-id)})]
    (->> (build-post-request (str (:url client) "/reports") params)
         (sign-request client)
         http/request)))

(defn generate-account-report
  [client start-date end-date account-id & opts]
  (let [params (merge opts 
                      {:type "account"
                       :start_date start-date
                       :end_date end-date
                       :account_id (clojure.string/upper-case account-id)})]
    (->> (build-post-request (str (:url client) "/reports") params)
         (sign-request client)
         http/request)))

(defn get-report-status
  [client report-id]
  (->> (build-get-request (str (:url client) "/reports/" report-id))
       (sign-request client)
       http/request))

(defn get-trailing-volume
  [client]
  (->> (build-get-request (str (:url client) "/users/self/trailing-volume"))
       (sign-request client)
       http/request))

;; ## Websocket feed

(def default-channels ["full"])

(defn- get-subscribe-message
  [opts]
  (let [message {:type "subscribe" 
                 :product_ids (:product_ids opts) 
                 :channels (or (:channels opts) default-channels)}]
    (if (contains-many? opts :key :secret :passphrase)
      (sign-message message opts)
      message)))

(defn- get-unsubscribe-message
  [product_ids & channels]
  {:type "unsubscribe" 
   :product_ids product_ids 
   :channels channels})

;; - `opts` will take the following shape
;; {:product_ids
;;  :channels (optional)
;;  :url (optional)
;;  :key (optional)
;;  :secret (optional)
;;  :passphrase (optional)}
(defn subscribe
  [connection opts]
  (->> (get-subscribe-message opts)
       edn->json
       (ws/send-msg connection)))

(defn unsubscribe
  [connection opts]
  (->> (get-unsubscribe-message opts)
       edn->json
       (ws/send-msg connection)))

(defn close
  [connection]
  (ws/close connection))

(defn- get-socket-connection
  [url opts]
  (let [client (WebSocketClient. (SslContextFactory.))]
    (.setMaxTextMessageSize (.getPolicy client) (* 1024 1024))
    (.start client)
    (ws/connect
      url
      :client client
      :on-connect (or (:on-connect opts) (constantly nil))
      :on-receive (or (:on-receive opts) (constantly nil))
      :on-close (or (:on-close opts) (constantly nil))
      :on-error (or (:on-error opts) (constantly nil)))))

;; - `callbacks` will take the following shape
;; {}
;; - `opts` will take the following shape
;; {:channels
;;  :sandbox
;;  :on-connect
;;  :on-receive
;;  :on-close
;;  :on-error
;;  :key
;;  :secret
;;  :passphrase}
(defn create-websocket-connection
  [product_ids & [opts]]
  (let [url (if (:sandbox opts) sandbox-websocket-url websocket-url)
        connection (get-socket-connection url opts)]
    ;; subscribe immediately so the connection isn't lost
    (subscribe connection (merge {:product_ids product_ids} opts))
    connection))





