(ns optiontrader.routes.home
  (:require [optiontrader.layout :as layout]
            [compojure.core :refer [defroutes GET]]
            [ring.util.http-response :as response]
            [ring.util.http-response :refer [ok found]]
            [org.httpkit.server
            :refer [send! with-channel on-close on-receive]]
            [taoensso.timbre :as timbre]
            [org.httpkit.client :as http]
            [clojure.data.json :as json]
            [digest :as digest]
            [cognitect.transit :as t]
            [clojure.java.io :as io]
            [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go chan buffer close! thread
                     alts! alts!! alt!! timeout]]))

(def api-secret "apxfhy007c5kobjzrm4vvdni18vejobo")
(def api-key "c2v7fui7p2igjtjw")

(def app-state (atom {:access-token "xxx" :user_id "yyy" :public_token "zzz"}))

;;
;; websockets

;;

(defn home-page []
  (layout/render "home.html"))

(defn handle-margin-query []
  (let [access_token (:access-token @app-state)
        margin_url (str "https://api.kite.trade/user/margins/equity?api_key=" api-key "&access_token=" access_token)
        ]
    (timbre/info "Margin URL - " margin_url)
    (timbre/info "Margin - " (:body @(http/get margin_url))))
     {:status 200 :headers {"location" "/#/"}})

(defn get-quote [scrip]
  (let [access_token (:access-token @app-state)
        url-to-fetch (str "https://api.kite.trade/instruments/NFO/" scrip "?api_key=" api-key "&access_token=" access_token)]
    (timbre/info url-to-fetch)
    (:last_price (:data (json/read-str (:body @(http/get url-to-fetch)) :key-fn keyword)))))
  

(defn handle-nifty-query []
  (timbre/info "handle-nifty-query App-state " @app-state)
    (def quote-resp {:nifty7800ce (get-quote "NIFTY16AUG7800CE")
      :nifty7900ce (get-quote "NIFTY16AUG7900CE")
      :nifty8000ce (get-quote "NIFTY16AUG8000CE")
      :nifty8100ce (get-quote "NIFTY16AUG8100CE")
      :nifty8200ce (get-quote "NIFTY16AUG8200CE")
      :nifty8300ce (get-quote "NIFTY16AUG8300CE")
      :nifty8400ce (get-quote "NIFTY16AUG8400CE")
      :nifty8500ce (get-quote "NIFTY16AUG8500CE")
      :nifty8600ce (get-quote "NIFTY16AUG8600CE")
      :nifty8700ce (get-quote "NIFTY16AUG8700CE")
      :nifty8800ce (get-quote "NIFTY16AUG8800CE")
      :nifty8900ce (get-quote "NIFTY16AUG8900CE")
      :nifty9000ce (get-quote "NIFTY16AUG9000CE")
      })
    (timbre/info "Nifty  quotes - " quote-resp)
    ;(timbre/info "Nifty quote - " (:body @(http/get margin_url))))
    (json/write-str {:status 200 :headers {"location" "/#/"}}))

(defn get_quote_xxx [scrip]
  (let [query_chan (chan)]
    (go (timbre/info "sleeping...")
        ;(Thread/sleep (rand-int 5000))
        (>! query_chan {(keyword scrip) (get-quote scrip)})
    )
    query_chan))

(defn get_quote_xxx_old [scrip]
  (let [query_chan (chan)]
    (go (timbre/info "sleeping...")
        ;(Thread/sleep (rand-int 5000))
        (>! query_chan {:value (get-quote scrip)})
    )
    query_chan))

(defn get-quote-xyz [scrip]
  (let [query_chan (chan)]
    (go (timbre/info "sleeping...")
        ;(Thread/sleep (rand-int 5000))
        (>! query_chan {:value (get-quote scrip)})
    )
    query_chan))

(defn handle-nifty-quotes []
  (timbre/info "handle-nifty-quotes App-state " @app-state)
    (def quote-resp {:nifty7800ce (<!! (get-quote-xyz "NIFTY16AUG7800CE"))
      :nifty7900ce (<!! (get-quote-xyz "NIFTY16AUG7900CE"))
      :nifty8000ce (<!! (get-quote-xyz "NIFTY16AUG8000CE"))
      :nifty8100ce (<!! (get-quote-xyz "NIFTY16AUG8100CE"))
      :nifty8200ce (<!! (get-quote-xyz "NIFTY16AUG8200CE"))
      :nifty8300ce (<!! (get-quote-xyz "NIFTY16AUG8300CE"))
      :nifty8400ce (<!! (get-quote-xyz "NIFTY16AUG8400CE"))
      :nifty8500ce (<!! (get-quote-xyz "NIFTY16AUG8500CE"))
      :nifty8600ce (<!! (get-quote-xyz "NIFTY16AUG8600CE"))
      :nifty8700ce (<!! (get-quote-xyz "NIFTY16AUG8700CE"))
      :nifty8800ce (<!! (get-quote-xyz "NIFTY16AUG8800CE"))
      :nifty8900ce (<!! (get-quote-xyz "NIFTY16AUG8900CE"))
      :nifty9000ce (<!! (get-quote-xyz "NIFTY16AUG9000CE"))
      })
    (timbre/info "Nifty  quotes - " quote-resp)
    ;(timbre/info "Nifty quote - " (:body @(http/get margin_url))))
    quote-resp)

(defn handle-nifty-quotes-old []
  (timbre/info "handle-nifty-quotes App-state " @app-state)
    (def quote-resp {:nifty7800ce (get-quote "NIFTY16AUG7800CE")
      :nifty7900ce (get-quote "NIFTY16AUG7900CE")
      :nifty8000ce (get-quote "NIFTY16AUG8000CE")
      :nifty8100ce (get-quote "NIFTY16AUG8100CE")
      :nifty8200ce (get-quote "NIFTY16AUG8200CE")
      :nifty8300ce (get-quote "NIFTY16AUG8300CE")
      :nifty8400ce (get-quote "NIFTY16AUG8400CE")
      :nifty8500ce (get-quote "NIFTY16AUG8500CE")
      :nifty8600ce (get-quote "NIFTY16AUG8600CE")
      :nifty8700ce (get-quote "NIFTY16AUG8700CE")
      :nifty8800ce (get-quote "NIFTY16AUG8800CE")
      :nifty8900ce (get-quote "NIFTY16AUG8900CE")
      :nifty9000ce (get-quote "NIFTY16AUG9000CE")
      })
    (timbre/info "Nifty  quotes - " quote-resp)
    ;(timbre/info "Nifty quote - " (:body @(http/get margin_url))))
    quote-resp)

(defn handle-nifty-quote [scrip]
  (timbre/info "handle-nifty-quote App-state " @app-state)
    (def quote-resp {:niftyscrip (get-quote (str "NIFTY16AUG" scrip))
      })
    (timbre/info "Nifty  quotes - " quote-resp)
    ;(timbre/info "Nifty quote - " (:body @(http/get margin_url))))
    quote-resp)


(defn handle-zerodha-response [req]
  (timbre/info "SUCCESS LOGGING IN")
  (timbre/info "Request token " (:request_token (:params req)))

  (let [request-token (:request_token (:params req))
        checksum (digest/sha-256 (str "c2v7fui7p2igjtjw" request-token api-secret))
        query-params {:query-params {:api_key "c2v7fui7p2igjtjw" :request_token request-token :checksum checksum}}
        body-str  (:body @(http/post "https://api.kite.trade/session/token" query-params))
        body     (json/read-str body-str :key-fn keyword)
        ]
      (timbre/info "Body " (type body) body)
      (timbre/info "Data " (:data body))
      (timbre/info "Status " (:status body))
      (timbre/info "Access_token " (:access_token (:data body)))
      (timbre/info "UserId " (:user_id (:data body)))
      (timbre/info "PublicToken " (:public_token (:data body)))
      (swap! app-state assoc-in [:user_id] (:user_id (:data body)))
      (swap! app-state assoc-in [:user_name] (:user_name (:data body)))
      (swap! app-state assoc-in [:public_token] (:public_token (:data body)))
      (swap! app-state assoc-in [:access-token] (:access_token (:data body)))
      (timbre/info "App-state " @app-state)
     ; (make-websocket! (str "wss://websocket.kite.trade/?api_key=" api-key "&user_id=" (:user_id @app-state) "&public_token=" (:public_token @app-state)) update-messages!)
      ))


(defroutes home-routes
  (GET "/" [] (timbre/info "Accessed Home ")(home-page))

  (GET "/get_ws_url" [] (response/ok {:wsurl (str "wss://websocket.kite.trade/?api_key=" api-key "&user_id=" (:user_id @app-state) "&public_token=" (:public_token @app-state))}))

  (GET "/margin" [] (timbre/info "Accessed Margin ") (response/ok (handle-margin-query)))

  (GET "/nifty" [] (timbre/info "Accessed Nifty ") (response/ok (handle-nifty-query)))

  (GET "/get_quotes" [] (timbre/info "Accessed Nifty Quotes ") (response/ok (handle-nifty-quotes)))

  (GET "/get_quote" req 
                     (let [scrip (:scrip (:params req))]
                           (timbre/info "Query Param is -> " scrip)
                           (timbre/info "Accessed Nifty Quotes ") 
                           (response/ok (handle-nifty-quote scrip))))

  (GET "/get_quote_xxx" req 
                     (let [scrip (:scrip (:params req))]
                           (timbre/info "Query Param is -> " scrip)
                           (timbre/info "Accessed Nifty Quotes ") 
                           (response/ok (<!! (get_quote_xxx (str "NIFTY16AUG" scrip))))))

  (GET "/zerodha-login" [] (timbre/info "In SYNC FB 123 :" )
        (found (str "https://kite.trade/connect/login?api_key=" api-key)))

  (GET "/zerodha_cb" req
                   (timbre/info "In zerodha Callback :"  (:params req))
                   (if (= (:status (:params req)) "success")
                        (handle-zerodha-response req)
                        (timbre/info "ERROR LOGGING IN "))
                   
                   (def mycookies (str "token=wss://websocket.kite.trade/?api_key=" api-key "&user_id=" (:user_id @app-state) "&public_token=" (:public_token @app-state)
                                   ",username=" (:user_name @app-state)))

                   (def wscookie (str "wss://websocket.kite.trade/?api_key=" api-key "&user_id=" (:user_id @app-state) "&public_token=" (:public_token @app-state))
                           )
                  ;(timbre/info wscookie)
                  (timbre/info (str "Setting Cookies - " (:public_token @app-state) "user_id" (:user_id @app-state) "username" (:user_name @app-state)))
                   {:status 302
                   ; :cookies {"publictoken" (:public_token @app-state) "user_id" (:user_id @app-state) "username" (:user_name @app-state)}
                    :cookies {"user_id" (:user_id @app-state)}
                    
                    :headers {"location" "/#/"
                     ;"set-cookie"  mycookies
                     }}
                     
                     )
  (GET "/docs" [] (response/ok (-> "docs/docs.md" io/resource slurp))))

