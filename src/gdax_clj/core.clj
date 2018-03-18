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

;; ## Convenience values

(def granularities {:1m 60
                    :5m 300
                    :15m 900
                    :1h 3600
                    :6h 21600
                    :1d 86400})

;; ## Public endpoints

;; - `client` will take the following shape
;; {:api-url
;;  :api-key
;;  :api-secret
;;  :pass-phrase}

(defn get-time
  [client]
  (http/request (build-get-request (str (:api-url client) "/time"))))

(defn get-products
  [client]
  (http/request (build-get-request (str (:api-url client) "/products"))))

(defn get-order-book
  ([client product-id]
   (get-order-book client product-id 1))
  ([client product-id level]
   (->> (str (:api-url client) "/products/" product-id "/book?level=" level)
        build-get-request
        http/request)))
  
(defn get-ticker
  [client product-id]
  (->> (str (:api-url client) "/products/" product-id "/ticker")
       build-get-request
       http/request))

(defn get-trades
  [client product-id]
  (->> (str (:api-url client) "/products/" product-id "/trades")
       build-get-request
       http/request))
    
(defn get-historic-rates
  ([client product-id]
   (->> (str (:api-url client) "/products/" product-id "/stats")
        build-get-request
        http/request))
  ([client product-id start end granularity]
   (->> (str (:api-url client) "/products/" product-id "/candles?start="
           start "&end=" end "&granularity=" granularity)
        build-get-request
        http/request)))
       
(defn get-product-stats
  [client product-id]
  (->> (str (:api-url client) "/products/" product-id "/stats")
       build-get-request
       http/request))
     
(defn get-currencies
  [client]
  (http/request (build-get-request (str (:api-url client) "/currencies"))))
  

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;; Authentication ;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- parse-request-path
  [request-url]
  (second (clojure.string/split request-url #".com")))

(defn- create-prehash-string
  [timestamp request]
  (str timestamp (clojure.string/upper-case (:method request)) 
    (parse-request-path (:url request)) (:body request)))

(def request {:method "get" 
              :url "https://api-public.sandbox.gdax.com/orders"
              :body {:side "buy"
                     :product-id "btc-usd"
                     :price "11500.00"
                     :size 2}})
                    
(defn- create-signature
  [client timestamp request]
  (let [secret-decoded (b64/decode (.getBytes (:api-secret client)))
        prehash-string (create-prehash-string timestamp request)
        hmac (sha256-hmac* prehash-string secret-decoded)]
    (-> hmac
        b64/encode
        String.)))

(defn- sign-request 
  [client request]
  (let [timestamp (quot (System/currentTimeMillis) 1000)]
    (update-in request [:headers] merge {"CB-ACCESS-KEY" (:api-key client)
                                         "CB-ACCESS-SIGN" (create-signature client timestamp request)
                                         "CB-ACCESS-TIMESTAMP" timestamp
                                         "CB-ACCESS-PASSPHRASE" (:api-passphrase client)})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;; Private Endpoints ;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;; Accounts ;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-accounts 
  [client]
  (->> (build-get-request "/accounts")
       (sign-request client)
       http/request))

(defn get-account-by-id
  [client account-id]
  (->> (build-get-request (str "/accounts/" account-id))
       (sign-request client)
       http/request))

(defn get-account-history
  [client account-id & [paging-options]]
  (->> (build-get-request (str "/accounts/" account-id "/ledger"))
       (append-query-params paging-options)
       (sign-request client)
       http/request))

(defn get-account-holds
  [client account-id & [paging-options]]
  (->> (build-get-request (str "/accounts/" account-id "/holds"))
       (append-query-params paging-options)
       (sign-request client)
       http/request))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;; Orders ;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn place-order
  [client side product-id & [options]]
  (let [body (merge options {:side side
                             :product_id (clojure.string/upper-case product-id)})]
    (->> (build-post-request "/orders" body)
         (sign-request client)
         http/request)))

(defn place-limit-order
  [client side product-id price size & [options]]
  (place-order side product-id (merge options {:price price
                                               :size size
                                               :type "limit"})))

(defn place-market-order
  [client side product-id & [options]]
  (place-order side product-id (merge options {:type "market"})))

(defn place-stop-order
  [client side product-id price & [options]]
  (place-order side product-id (merge options {:type "stop"
                                               :price price})))

(defn get-orders
  [client & {:keys [statuses] :as options}]
  (let [query-string (clojure.string/join "&" (map #(str "status=" (name %)) statuses))
        rest-options (dissoc options :statuses)]
    (->> (build-get-request (str "/orders"
                                 (when-not (clojure.string/blank? query-string) "?")
                                 query-string))
         (append-query-params rest-options)
         (sign-request client)
         http/request)))

(defn cancel-order
  [client order-id]
  (->> (build-delete-request (str "/orders/" order-id))
       (sign-request client)
       http/request))

(defn cancel-all
  [client & [product-id]]
  (->> (build-delete-request 
          (str "/orders" (when-not (nil? product-id) (str "?product_id=" product-id))))
       (sign-request client)
       http/request))

(defn get-order
  [client order-id]
  (->> (build-get-request (str "/orders/" order-id))
       (sign-request client)
       http/request))

(defn get-fills
  [client & [options]]
  (->> (build-get-request "/fills")
       (append-query-params options)
       (sign-request client)
       http/request))

(defn get-payment-methods
  [client]
  (->> (build-get-request "/payment-methods")
       (sign-request client)
       http/request))

(defn get-coinbase-accounts
  [client]
  (->> (build-get-request "/coinbase-accounts")
       (sign-request client)
       http/request))

(defn deposit-from-coinbase
  [client amount currency coinbase-account-id]
  (->> (build-post-request 
         "/deposits/coinbase-account" 
         {:amount amount
          :currency (clojure.string/upper-case currency)
          :coinbase_account_id coinbase-account-id})
       (sign-request client)
       http/request))

(defn withdraw-to-coinbase
  [client amount currency coinbase-account-id]
  (->> (build-post-request 
         "/withdrawals/coinbase-account"
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
         "/withdrawals/crypto"
         {:amount amount
          :currency (clojure.string/upper-case currency)
          :crypto_address crypto-address})
       (sign-request client)
       http/request))

(defn generate-fills-report
  [client start-date end-date product-id & [options]]
  (let [params (merge options
                      {:type "fills"
                       :start_date start-date
                       :end_date end-date
                       :product_id (clojure.string/upper-case product-id)})]
    (->> (build-post-request "/reports" params)
         (sign-request client)
         http/request)))

;; TODO: test this method
(defn generate-account-report
  [client start-date end-date account-id & [options]]
  (let [params (merge options 
                      {:type "account"
                       :start_date start-date
                       :end_date end-date
                       :account_id (clojure.string/upper-case account-id)})]
    (->> (build-post-request "/reports" params)
         (sign-request client)
         http/request)))

(defn get-report-status
  [client report-id]
  (->> (build-get-request (str "/reports/" report-id))
       (sign-request client)
       http/request))

(defn get-trailing-volume
  [client]
  (->> (build-get-request "/users/self/trailing-volume")
       (sign-request client)
       http/request))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;; Websocket Feed ;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-subscribe-message
  [product-ids channels]
  {:type "subscribe" 
   :product_ids product-ids 
   :channels channels})

(defn- on-connect
  [session]
  (println "Connected to websocket." (pprint session)))

(defn- on-receive
  [message]
  (println "Received:" message))

(defn- on-error
  [error]
  (println "Error occurred:" error))

(defn- on-close
  [status-code reason]
  (println "Connection to websocket closed. Status code:" status-code ". Reason:" reason))

(defn- get-socket
  [url]
  (let [client (WebSocketClient. (SslContextFactory.))]
    (.setMaxTextMessageSize (.getPolicy client) (* 1024 1024))
    (.start client)
    (ws/connect
      (:websocket-url url)
      :client client
      :on-connect on-connect
      :on-receive on-receive
      :on-error on-error
      :on-close on-close)))

(defn subscribe
  [client product-ids channels]
  (let [socket (get-socket (:websocket-url client))]
    (ws/send-msg socket (json/write-str (get-subscribe-message product-ids channels)))
    {:close #(ws/close socket)}))



