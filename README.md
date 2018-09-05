[![Build Status](https://travis-ci.org/bpringe/coinbase-pro-clj.svg?branch=master)](https://travis-ci.org/bpringe/coinbase-pro-clj)
[![Clojars Project](https://img.shields.io/clojars/v/coinbase-pro-clj.svg)](https://clojars.org/coinbase-pro-clj)

# coinbase-pro-clj

A Clojure wrapper for the [Coinbase Pro API](https://docs.pro.coinbase.com/) (formerly GDAX). The point of this library is to make it convenient to harness the power of Clojure
for creating cryptocurrency trading bots and similar applications that utilize the [Coinbase Pro exchange](https://pro.coinbase.com). Results are given in edn, and authentication is handled by the library.

## Installation

Add the dependency to your project or build file. Then run `lein deps`.

```clojure
[coinbase-pro-clj "0.2.0"]
```

## Quick Start

Read on for a quick start or jump to the detailed [documentation](https://bpringe.github.io/coinbase-pro-clj/index.html).

First, require it in the REPL:

```clojure
(require '[coinbase-pro-clj.core :as cp])
```

Or in your application:

```clojure
(ns my-app.core
  (:require [coinbase-pro-clj.core :as cp]))
```

Each function takes in a client, which requires a `:url` and optional authentication values. The authentication values are only required for authenticated endpoints. These can be obtained by logging into [Coinbase Pro](https://pro.coinbase.com) and creating them in the API settings section. URL vars are provided by the library for convenience.

```clojure
(def client {:url cp/rest-url ; Other values include websocket-url, sandbox-rest-url, and sanbox-websocket-url.
             :key "Coinbase Pro key"
             :secret "Coinbase Pro secret"
             :passphrase "Coinbase Pro passphrase"})
```

From here you can call any of the functions (provided your client has a valid key, secret, and passphrase for authenticated endpoints). Here are a few examples:

```clojure
;; Public endpoints
(cp/get-products client)

(cp/get-ticker client "BTC-USD")

;; Private endpoints (require authentication values in client)
(cp/get-orders client)

(cp/place-order client {:side "buy"
                        :product_id "BTC-USD"
                        :price 5000
                        :size 1})
```

See the [documentation](https://bpringe.github.io/coinbase-pro-clj/index.html) for a list of all endpoint functions with links the the Coinbase Pro API docs and code examples for each.

## Contributing

If you notice anything that is unclear or incorrect in this readme or the docs, feel free to create a pull request, or you can open an issue for me to correct it. If you would like a feature added or notice a bug, please open an issue. Thanks for contributing. =)

## Contact

If you have any questions or want to discuss the library, you can chat me up (@bringe) in the [#crypto-currencies](https://clojurians.slack.com/messages/C7N5B9LLT) channel of the Clojurians Slack.

## License

Copyright © 2018 Brandon Ringe

Distributed under the MIT License
