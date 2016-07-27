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
            [clojure.java.io :as io]))

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

(defn handle-nifty-query []
  (let [access_token (:access-token @app-state)
        margin_url (str "https://api.kite.trade/instruments/NSE/NIFTY16JUL8500CE?api_key=" api-key "&access_token=" access_token)
        ]
    (timbre/info "Nifty  URL - " margin_url)
    (timbre/info "Nifty quote - " (:body @(http/get margin_url))))
    (json/write-str {:status 200 :headers {"location" "/#/"}}))


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
                  (timbre/info wscookie)
                   {:status 302
                    :cookies {"token" wscookie "username" (:user_name @app-state)}
                    :headers {"location" "/#/"
                     ;"set-cookie"  mycookies
                     }}
                     
                     )
  (GET "/docs" [] (response/ok (-> "docs/docs.md" io/resource slurp))))

