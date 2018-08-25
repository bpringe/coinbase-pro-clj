(ns coinbase-pro-clj.core
  "Public and private endpoint functions and websocket feed functionality."
  (:require 
    [coinbase-pro-clj.utilities :refer :all]
    [coinbase-pro-clj.authentication :refer :all]
    [cheshire.core :refer :all]
    [clj-http.client :as http]
    [environ.core :refer [env]]
    [gniazdo.core :as ws])
  (:import (org.eclipse.jetty.websocket.client WebSocketClient)
           (org.eclipse.jetty.util.ssl SslContextFactory)))

;; ## Convenience/config values
(def ^:private rest-url "https://api.pro.coinbase.com")
(def ^:private websocket-url "wss://ws-feed.pro.coinbase.com")
(def ^:private sandbox-rest-url "https://api-public.sandbox.pro.coinbase.com")
(def ^:private sandbox-websocket-url "wss://ws-feed-public.sandbox.pro.coinbase.com")
(def ^:private default-channels ["heartbeat"])

; (def ^:private client {:url rest-url
;                        :key (env :key)
;                        :secret (env :secret)
;                        :passphrase (env :passphrase)})

(def ^:private test-client {:url sandbox-rest-url
                            :key (env :sandbox-key)
                            :secret (env :sandbox-secret)
                            :passphrase (env :sandbox-passphrase)})

(defn- send-request
  [request]
  (-> request
      http/request
      :body))    

;; ## Public endpoints

;; - `client` will take the following shape
;; {:url
;;  :key
;;  :secret
;;  :passphrase}

(defn get-time
  "[API docs](https://docs.pro.coinbase.com/#time)"
  [client]
  (-> (str (:url client) "/time")
      build-get-request
      send-request))

(defn get-products
  "[API docs](https://docs.pro.coinbase.com/#products)"
  [client]
  (-> (str (:url client) "/products")
      build-get-request
      send-request))    

(defn get-order-book
  "[API docs](https://docs.pro.coinbase.com/#get-product-order-book)
    ```clojure
    (get-order-book client \"BTC-USD\")
    (get-order-book client \"BTC-USD\" 2)
    ```"
  ([client product-id]
   (get-order-book client product-id 1))
  ([client product-id level]
   (-> (str (:url client) "/products/" product-id "/book?level=" level)
       build-get-request
       send-request)))
  
(defn get-ticker
  "[API docs](https://docs.pro.coinbase.com/#get-product-ticker)
    ```clojure
    (get-ticker client \"BTC-USD\")
    (get-ticker client \"BTC-USD\" {:before 2 :limit 5})
    ```"
  ([client product-id]
   (get-ticker client product-id {}))
  ([client product-id opts]
   (->> (str (:url client) "/products/" product-id "/ticker")
        build-get-request
        (append-query-params opts)
        send-request)))

(defn get-trades
  "[API docs](https://docs.pro.coinbase.com/#get-trades)
    ```clojure
    (get-trades client \"BTC-USD\")
    (get-trades client \"BTC-USD\" {:before 2 :limit 5})
    ```"
  ([client product-id]
   (get-trades client product-id {}))
  ([client product-id opts]
   (->> (str (:url client) "/products/" product-id "/trades")
        build-get-request
        (append-query-params opts)
        send-request)))
    
(defn get-historic-rates
  "[API docs](https://docs.pro.coinbase.com/#get-historic-rates)
```clojure
(get-historic-rates client \"BTC-USD\")
(get-historic-rates client \"BTC-USD\" {:start \"2018-06-01\"
                                        :end \"2018-06-30\"
                                        :granularity 86400})
```"
  ([client product-id]
   (get-historic-rates client product-id {}))
  ([client product-id opts]
   (->> (str (:url client) "/products/" product-id "/candles")
        build-get-request
        (append-query-params opts)
        send-request)))

(defn get-24hour-stats
  "[API docs](https://docs.pro.coinbase.com/#get-24hr-stats)
    ```clojure
    (get-24hour-stats client \"BTC-USD\")
    ```"
  [client product-id]
  (->> (str (:url client) "/products/" product-id "/stats")
       build-get-request
       send-request))
     
(defn get-currencies
  "[API docs](https://docs.pro.coinbase.com/#get-currencies)"
  [client]
  (send-request (build-get-request (str (:url client) "/currencies"))))

;; ## Private endpoints

(defn get-accounts
  "[API docs](https://docs.pro.coinbase.com/#list-accounts)"
  [client]
  (->> (build-get-request (str (:url client) "/accounts"))
       (sign-request client)
       send-request))

(defn get-account
  "[API docs](https://docs.pro.coinbase.com/#get-an-account)
    ```clojure
    (get-account client \"7d0f7d8e-dd34-4d9c-a846-06f431c381ba\")
    ```"
  [client account-id]
  (->> (build-get-request (str (:url client) "/accounts/" account-id))
       (sign-request client)
       send-request))

(defn get-account-history
  ([client account-id]
   (get-account-history client account-id {}))
  ([client account-id paging-opts]
   (->> (build-get-request (str (:url client) "/accounts/" account-id "/ledger"))
        (append-query-params paging-opts)
        (sign-request client)
        send-request)))

(defn get-account-holds
  ([client account-id]
   (get-account-holds client account-id {}))
  ([client account-id paging-opts]
   (->> (build-get-request (str (:url client) "/accounts/" account-id "/holds"))
        (append-query-params paging-opts)
        (sign-request client)
        send-request)))

(defn place-order
  [client opts]
  (->> (build-post-request (str (:url client) "/orders") opts)
       (sign-request client)
       send-request))

(defn get-orders
  ([client]
   (get-orders client {:status ["all"]}))
  ([client opts]
   (let [query-string (clojure.string/join "&" (map #(str "status=" %) (:status opts)))
         rest-opts (dissoc opts :status)]
    (->> (build-get-request (str (:url client)
                                 "/orders"
                                 (when-not (clojure.string/blank? query-string) "?")
                                 query-string))
         (append-query-params rest-opts)
         (sign-request client)
         send-request))))

(defn cancel-order
  [client order-id]
  (->> (build-delete-request (str (:url client) "/orders/" order-id))
       (sign-request client)
       send-request))

(defn cancel-all
  ([client]
   (cancel-all client nil))  
  ([client product-id]
   (->> (build-delete-request 
          (str (:url client) "/orders" (when-not (nil? product-id) (str "?product_id=" product-id))))
        (sign-request client)
        send-request)))

(defn get-order
  [client order-id]
  (->> (build-get-request (str (:url client) "/orders/" order-id))
       (sign-request client)
       send-request))

;; opts must contain either order_id or product_id
(defn get-fills
  ([client opts]
   (->> (build-get-request (str (:url client) "/fills"))
        (append-query-params opts)
        (sign-request client)
        send-request)))

(defn get-payment-methods
  [client]
  (->> (build-get-request (str (:url client) "/payment-methods"))
       (sign-request client)
       send-request))

(defn deposit-from-payment-method
  [client opts]
  (->> (build-post-request (str (:url client) "/deposits/payment-method") opts)
       (sign-request client)
       send-request))

(defn withdraw-to-payment-method
  [client opts]
  (->> (build-post-request 
          (str (:url client) "/withdrawals/payment-method")
          opts)
       (sign-request client)
       send-request))

(defn get-coinbase-accounts
  [client]
  (->> (build-get-request (str (:url client) "/coinbase-accounts"))
       (sign-request client)
       send-request))

(defn deposit-from-coinbase
  [client opts]
  (->> (build-post-request 
         (str (:url client) "/deposits/coinbase-account") 
         opts)
       (sign-request client)
       send-request))

(defn withdraw-to-coinbase
  [client opts]
  (->> (build-post-request 
         (str (:url client) "/withdrawals/coinbase-account")
         opts)
       (sign-request client)
       send-request))

(defn withdraw-to-crypto-address
  [client opts]
  (->> (build-post-request
         (str (:url client) "/withdrawals/crypto")
         opts)
       (sign-request client)
       send-request))

(defn generate-report
  [client opts]
  (->> (build-post-request (str (:url client) "/reports") opts)
       (sign-request client)
       send-request))

(defn get-report-status
  [client report-id]
  (->> (build-get-request (str (:url client) "/reports/" report-id))
       (sign-request client)
       send-request))

(defn get-trailing-volume
  [client]
  (->> (build-get-request (str (:url client) "/users/self/trailing-volume"))
       (sign-request client)
       send-request))

;; ## Websocket feed

(defn- get-subscribe-message
  [opts]
  (let [message {:type "subscribe" 
                 :product_ids (:product_ids opts) 
                 :channels (or (:channels opts) default-channels)}]
    (if (contains-many? opts :key :secret :passphrase)
      (sign-message message opts)
      message)))

(defn- get-unsubscribe-message
  ([product_ids]
   (get-unsubscribe-message product_ids []))
  ([product_ids channels]
   {:type "unsubscribe" 
    :product_ids product_ids 
    :channels channels}))

;; - `opts` will take the following shape
;; {:product_ids
;;  :channels (optional)
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
  [opts]
  (let [client (WebSocketClient. (SslContextFactory.))]
    (.setMaxTextMessageSize (.getPolicy client) (* 1024 1024))
    (.start client)
    (ws/connect
      (:url opts)
      :client client
      :on-connect (or (:on-connect opts) (constantly nil))
      :on-receive (or (:on-receive opts) (constantly nil))
      :on-close (or (:on-close opts) (constantly nil))
      :on-error (or (:on-error opts) (constantly nil)))))
      
;; - `opts` will take the following shape
;; {:url
;;  :product_ids
;;  :channels (optional)
;;  :key (optional)
;;  :secret (optional)
;;  :passphrase (optional)
;;  :on-connect (optional)
;;  :on-receive (optional)
;;  :on-close (optional)
;;  :on-error (optional)

(defn create-websocket-connection
  [opts]
  (let [connection (get-socket-connection opts)]
    ;; subscribe immediately so the connection isn't lost
    (subscribe connection opts)
    connection))

;; example code
(def websocket-opts {:product_ids ["BTC-USD"]
                     :url websocket-url
                     :on-receive (fn [x] (prn 'received x))})
(comment
  (def conn (create-websocket-connection websocket-opts))
  
  (close conn))

