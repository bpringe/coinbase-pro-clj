(ns coinbase-pro-clj.core
  "Public and private endpoint functions and websocket feed functionality. In all function signatures, `client` is a map with the following keys:
- `:url` - rest URL
- `:key` - optional - your Coinbase Pro API key
- `:secret` - optional - your Coinbase Pro API key
- `:passphrase` - optional - your Coinbase Pro API key

`key`, `secret`, and `passphrase` are only required if the request is authenticated. These values can be created in the [API settings](https://pro.coinbase.com/profile/api) of your Coinbase Pro account.
**Remember not to store these values in an online repository as this will give others access to your account. You could use something like [environ](https://github.com/weavejester/environ)
to store these values locally outside of your code.**"
  (:require
   [coinbase-pro-clj.utilities
    :refer [append-query-params
            build-get-request
            build-post-request
            build-delete-request
            json->edn
            edn->json
            contains-many?]]
   [coinbase-pro-clj.authentication :refer [sign-request
                                            sign-message]]
   [clj-http.client :as http]
   [gniazdo.core :as ws]
   [clojure.string :as str])
  (:import (org.eclipse.jetty.websocket.client WebSocketClient)
           (org.eclipse.jetty.util.ssl SslContextFactory)))

;; ## Convenience/config values
(def rest-url 
  "The rest URL for Coinbase Pro."
  "https://api.pro.coinbase.com")
(def websocket-url 
  "The websocket URL for Coinbase Pro."
  "wss://ws-feed.pro.coinbase.com")
(def sandbox-rest-url 
  "The sandbox rest URL for Coinbase Pro."
  "https://api-public.sandbox.pro.coinbase.com")
(def sandbox-websocket-url
  "The sandbox websocket URL for Coinbase Pro."
  "wss://ws-feed-public.sandbox.pro.coinbase.com")

(def ^:private default-channels 
  "Default channels for websocket subscriptions, used if none is explicitly stated."
  ["heartbeat"])

(defn- send-request
  "Takes in a request, sends the http request, and returns the body of the response."
  [request]
  (-> request
      http/request
      :body))

;; ## Public endpoints

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
  "[API docs](https://docs.pro.coinbase.com/#get-account-history)
```clojure
(get-account-history client \"7d0f7d8e-dd34-4d9c-a846-06f431c381ba\")
(get-account-history client \"7d0f7d8e-dd34-4d9c-a846-06f431c381ba\" {:before 2 :limit 5})
```"
  ([client account-id]
   (get-account-history client account-id {}))
  ([client account-id paging-opts]
   (->> (build-get-request (str (:url client) "/accounts/" account-id "/ledger"))
        (append-query-params paging-opts)
        (sign-request client)
        send-request)))

(defn get-account-holds
  "[API docs](https://docs.pro.coinbase.com/#get-holds)
```clojure
(get-account-holds client \"BTC-USD\" \"7d0f7d8e-dd34-4d9c-a846-06f431c381ba\")
(get-account-holds client \"7d0f7d8e-dd34-4d9c-a846-06f431c381ba\" {:before 2 :limit 5})
```"
  ([client account-id]
   (get-account-holds client account-id {}))
  ([client account-id paging-opts]
   (->> (build-get-request (str (:url client) "/accounts/" account-id "/holds"))
        (append-query-params paging-opts)
        (sign-request client)
        send-request)))

(defn place-order
  "[API docs](https://docs.pro.coinbase.com/#place-a-new-order)
```clojure
(place-order client {:side \"buy\"
                     :product_id \"BTC-USD\"
                     :price 5000
                     :size 1})
```"
  [client opts]
  (->> (build-post-request (str (:url client) "/orders") opts)
       (sign-request client)
       send-request))

(defn get-orders
  "[API docs](https://docs.pro.coinbase.com/#list-orders)
```clojure
(get-orders client {:status [\"open\" \"pending\"]})
```"
  ([client]
   (get-orders client {:status ["all"]}))
  ([client opts]
   (let [query-string (str/join "&" (map #(str "status=" %) (:status opts)))
         rest-opts (dissoc opts :status)]
    (->> (build-get-request (str (:url client)
                                 "/orders"
                                 (when-not (str/blank? query-string) "?")
                                 query-string))
         (append-query-params rest-opts)
         (sign-request client)
         send-request))))

(defn cancel-order
  "[API docs](https://docs.pro.coinbase.com/#cancel-an-order)
```clojure
(cancel-order client \"7d0f7d8e-dd34-4d9c-a846-06f431c381ba\")
```"   
  [client order-id]
  (->> (build-delete-request (str (:url client) "/orders/" order-id))
       (sign-request client)
       send-request))

(defn cancel-all
  "[API docs](https://docs.pro.coinbase.com/#cancel-all)
```clojure
(cancel-all client \"BTC-USD\")
```"   
  ([client]
   (cancel-all client nil))  
  ([client product-id]
   (->> (build-delete-request 
          (str (:url client) "/orders" (when-not (nil? product-id) (str "?product_id=" product-id))))
        (sign-request client)
        send-request)))

(defn get-order
  "[API docs](https://docs.pro.coinbase.com/#get-an-order)
```clojure
(get-order client \"7d0f7d8e-dd34-4d9c-a846-06f431c381ba\")
```"
  [client order-id]
  (->> (build-get-request (str (:url client) "/orders/" order-id))
       (sign-request client)
       send-request))

;; opts must contain either order_id or product_id
(defn get-fills
  "[API docs](https://docs.pro.coinbase.com/#list-fills)
     
Opts must contain either `:order_id` or `:product_id`.
   
```clojure
(get-fills client {:product_id \"BTC-USD\" :before 2})
```"
  ([client opts]
   (->> (build-get-request (str (:url client) "/fills"))
        (append-query-params opts)
        (sign-request client)
        send-request)))

(defn get-payment-methods
  "[API docs](https://docs.pro.coinbase.com/#list-payment-methods)"
  [client]
  (->> (build-get-request (str (:url client) "/payment-methods"))
       (sign-request client)
       send-request))

(defn deposit-from-payment-method
  "[API docs](https://docs.pro.coinbase.com/#payment-method)
```clojure
(deposit-from-payment-method client {:amount 10
                                     :currency \"USD\"
                                     :payment_method_id \"7d0f7d8e-dd34-4d9c-a846-06f431c381ba\"})
```"
  [client opts]
  (->> (build-post-request (str (:url client) "/deposits/payment-method") opts)
       (sign-request client)
       send-request))

(defn withdraw-to-payment-method
  "[API docs](https://docs.pro.coinbase.com/#payment-method48)
```clojure
(withdraw-to-payment-method client {:amount 10
                                    :currency \"BTC-USD\"
                                    :payment_method_id \"7d0f7d8e-dd34-4d9c-a846-06f431c381ba\"})  
```" 
  [client opts]
  (->> (build-post-request 
          (str (:url client) "/withdrawals/payment-method")
          opts)
       (sign-request client)
       send-request))

(defn get-coinbase-accounts
  "[API docs](https://docs.pro.coinbase.com/#list-accounts54)"
  [client]
  (->> (build-get-request (str (:url client) "/coinbase-accounts"))
       (sign-request client)
       send-request))

(defn deposit-from-coinbase
  "[API docs](https://docs.pro.coinbase.com/#coinbase)
```clojure
(deposit-from-coinbase client {:amount 2
                               :currency \"BTC\"
                               :coinbase_account_id \"7d0f7d8e-dd34-4d9c-a846-06f431c381ba\"})
```"    
  [client opts]
  (->> (build-post-request 
         (str (:url client) "/deposits/coinbase-account") 
         opts)
       (sign-request client)
       send-request))

(defn withdraw-to-coinbase
  "[API docs](https://docs.pro.coinbase.com/#coinbase49)
```clojure
(withdraw-to-coinbase client {:amount 2
                              :currency \"BTC\"
                              :coinbase_account_id \"7d0f7d8e-dd34-4d9c-a846-06f431c381ba\"})
```"
  [client opts]
  (->> (build-post-request 
         (str (:url client) "/withdrawals/coinbase-account")
         opts)
       (sign-request client)
       send-request))

(defn withdraw-to-crypto-address
  "[API docs](https://docs.pro.coinbase.com/#crypto)
```clojure
(withdraw-to-crypto-address client {:amount 2
                                    :currency \"BTC\"
                                    :crypto_address \"15USXR6S4DhSWVHUxXRCuTkD1SA6qAdy\"})
```"
  [client opts]
  (->> (build-post-request
         (str (:url client) "/withdrawals/crypto")
         opts)
       (sign-request client)
       send-request))

(defn generate-report
  "[API docs](https://docs.pro.coinbase.com/#create-a-new-report)
```clojure
(generate-report client {:type \"fills\"
                         :start_date \"2018-6-1\"
                         :end_date \"2018-6-30\"
                         :product_id \"BTC-USD\"})
```"      
  [client opts]
  (->> (build-post-request (str (:url client) "/reports") opts)
       (sign-request client)
       send-request))

(defn get-report-status
  "[API docs](https://docs.pro.coinbase.com/#get-report-status)
```clojure
(get-report-status client \"7d0f7d8e-dd34-4d9c-a846-06f431c381ba\")
```"
  [client report-id]
  (->> (build-get-request (str (:url client) "/reports/" report-id))
       (sign-request client)
       send-request))

(defn get-trailing-volume
  "[API docs](https://docs.pro.coinbase.com/#trailing-volume)"
  [client]
  (->> (build-get-request (str (:url client) "/users/self/trailing-volume"))
       (sign-request client)
       send-request))

;; ## Websocket feed

(defn- create-subscribe-message
  "Creates the subscribe message and signs the message if key, secret, and passphrase are provided."
  [opts]
  (let [message {:type "subscribe" 
                 :product_ids (:product_ids opts) 
                 :channels (or (:channels opts) default-channels)}]
    (if (contains-many? opts :key :secret :passphrase)
      (sign-message message opts)
      message)))

(defn- create-unsubscribe-message
  "Creates the unsubscribe message."
  [opts]
  (merge opts {:type "unsubscribe"}))

(defn subscribe
  "[API docs](https://docs.pro.coinbase.com/#subscribe)
     
- `connection` is created with [[create-websocket-connection]]
- `opts` is a map with the following keys:
    - `:product_ids` - either this or `:channels` or both must be provided (see the Coinbase Pro API docs) - a vector of strings
    - `:channels` - either this or `:product_ids` or both must be provided (see the Coinbase Pro API docs) - a vector of strings or maps with `:name` (string) and `:product_ids` (vector of strings)
    - `:key` - optional - your Coinbase Pro API key
    - `:secret` - optional - your Coinbase Pro API key
    - `:passphrase` - optional - your Coinbase Pro API key
  
```clojure
(subscribe connection {:product_ids [\"BTC-USD\"]})
```"
  [connection opts]
  (->> (create-subscribe-message opts)
       edn->json
       (ws/send-msg connection)))

(defn unsubscribe
  "[API docs](https://docs.pro.coinbase.com/#subscribe)

- `connection` is created with [[create-websocket-connection]].
- `opts` takes the equivalent shape as [[subscribe]].

```clojure
(unsubscribe connection {:product_ids [\"BTC-USD\"]})
```"
  [connection opts]
  (->> (create-unsubscribe-message opts)
       edn->json
       (ws/send-msg connection)))

(defn close
  "`connection` is created with [[create-websocket-connection]]."
  [connection]
  (ws/close connection))

(defn- create-on-receive
  "Returns a function that returns nil if user-on-receive is nil, otherwise returns a function 
  that takes the received message, converts it to edn, then passes it to the user-on-receive function"
  [user-on-receive]
  (if (nil? user-on-receive)
    (constantly nil)
    (fn [msg]
      (-> msg
          json->edn ; convert to edn before passing to user defined on-receive
          user-on-receive))))
        
(defn- get-socket-connection
  "Creates the socket client using the Java WebSocketClient and SslContextFactory, starts the client,
  then connects it to the websocket URL."
  [opts]
  (let [client (WebSocketClient. (SslContextFactory.))]
    (.setMaxTextMessageSize (.getPolicy client) (* 1024 1024))
    (.start client)
    (ws/connect
      (:url opts)
      :client client
      :on-connect (or (:on-connect opts) (constantly nil))
      :on-receive (create-on-receive (:on-receive opts))
      :on-close (or (:on-close opts) (constantly nil))
      :on-error (or (:on-error opts) (constantly nil)))))

(defn create-websocket-connection
  "[API docs](https://docs.pro.coinbase.com/#websocket-feed)

`opts` is a map that takes the following keys:

- `:url` - the websocket URL
- `:product_ids` - either this or `:channels` or both must be provided (see the Coinbase Pro API docs) - a vector of strings 
- `:channels` - either this or `:product_ids` or both must be provided (see the Coinbase Pro API docs) - a vector of strings or maps with `:name` (string) and `:product_ids` (vector of strings)
- `:key` - optional - your Coinbase Pro API key 
- `:secret` - optional - your Coinbase Pro API secret
- `:passphrase` - optional - your Coinbase Pro API passphrase
- `:on-receive` - optional - A unary function called when a message is received. The argument is received as edn.
- `:on-connect` - optional - A unary function called after the connection has been established. The argument is a [WebSocketSession](https://www.eclipse.org/jetty/javadoc/9.4.8.v20171121/org/eclipse/jetty/websocket/common/WebSocketSession.html).
- `:on-error` - optional - A unary function called in case of errors. The argument is a `Throwable` describing the error.
- `:on-close` - optional - A binary function called when the connection is closed. Arguments are an `int` status code and a `String` description of reason.    

```clojure
(def conn (create-websocket-connection {:product_ids [\"BTC-USD\"]
                                        :url \"wss://ws-feed.pro.coinbase.com\"
                                        :on-receive (fn [x] (prn 'received x))}))
```"
  [opts]
  (let [connection (get-socket-connection opts)]
    ;; subscribe immediately so the connection isn't lost
    (subscribe connection opts)
    connection))

