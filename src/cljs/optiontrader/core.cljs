(ns optiontrader.core
  (:require [reagent.core :as reagent]
            [reagent.cookies :as cookies]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [optiontrader.handlers]
            [optiontrader.subs]
            [datascript.core :as d]
            [clojure.core.matrix :as mat]
            [cljs.core.async :as async :refer [chan close!]]
            [cljs.core.match :refer-macros [match]]
            [goog.history.EventType :as HistoryEventType]
            [markdown.core :refer [md->html]]
            [optiontrader.ajax :refer [load-interceptors!]]
            [ajax.core :refer [GET POST]]
            [cljs.reader :as reader]
            [clojure.walk :as walk]
            [cljsjs.highcharts :as highcharts]
            [re-frame.core :as re-frame]
            [posh.reagent :refer [pull q posh!]]
            [cognitect.transit :as t]
            [cljsjs.material-ui]
            [cljs-react-material-ui.core :as ui]
            [cljs-react-material-ui.reagent :as rui]
            [cljs-react-material-ui.icons :as ic])
  (:import goog.History)
  (:require-macros
    [cljs.core.async.macros :refer [go alt!]]))

;;posh related
(comment
(def app-schema 
          {:company/logo {:db/type :db.type/string}
           :strategy/name {:db/type :db.type/string}
           :strategy/id {:db/unique :db.unique/identity}
           :strategy/description {:db/type :db.type/string}

           :option/strategy {:db/type :db.type/ref}
           :option/id {:db/unique :db.unique/identity}
           :option/optiontype {:db/type :db.type/string}
           :option/ordertype {:db/type :db.type/string}
           :option/premium {:db/type :db.type/double}

           :executed-order/strategy {:db/type :db.type/ref}
           :executed-order/id {:db/unique :db.unique/identity} })

(def conn (d/create-conn app-schema))
(posh! conn)
(def tempid (let [n (atom 0)] (fn [] (swap! n dec))))

(def counter (atom 0))
(def apikey "c2v7fui7p2igjtjw")
(defn next-idx []
  (swap! counter inc))

(defn new-entity! [conn varmap]
  ((:tempids (d/transact! conn [(merge varmap {:db/id -1})])) -1))


(defn add-strategies []
  (d/transact! conn [
    {:strategy/name {:db/type :db.type/string}
     :strategy/id (next-idx)
     :strategy/description {:db/type :db.type/string}

     :option/strategy {:db/type :db.type/ref}
     :option/id (next-idx) 
     :option/optiontype {:db/type :db.type/string}
     :option/ordertype {:db/type :db.type/string}
     :option/premium {:db/type :db.type/double}}
    ])
  )
)
;;
;;http calls

(def apikey "c2v7fui7p2igjtjw")
(defn log [s]
  (.log js/console (str s)))


(def url_list {:usertoken1
                 (fn [username password]
                   (str "/api/getUserToken?grant_type=password&username=" username "&password=" password))
               :usertoken
                 (fn [username password]
                   (str "/usertoken?grant_type=password&username=" username "&password=" password))
               :user_register
                 (fn [username passwd rt_passwd email]
                    (str "/register"))
              :zerodha_login
                 (fn []
                    (str "/zerodha-login"))
              :get_ws_url
                 (fn []
                    (str "/get_ws_url"))})

(def read-json (t/reader :json))

(def write-json (t/writer :json))

(defn read-server-response [response]
  (walk/keywordize-keys (t/read read-json response)))

(defn response-handler [ch response]
  (go (>! ch response)(close! ch))
  (log "DONE"))

(defn do-http-get [url]
  (log (str "GET " url))
  (let [ch (chan 1)]
    (GET url {:handler (fn [response](response-handler ch response))
              :error-handler (fn [response] (response-handler ch response))})
    ch))


(defn do-http-post [url doc]
  (log "POSTING ---->")
  (log (str "POST " url (clj->js doc)))
  (let [ch (chan 1)]
    (POST url {:params  (clj->js doc) :format :json :handler (fn [response] (response-handler ch response))
               :error-handler (fn [response] (response-handler ch response))})
    ch))



(defn read-login-response [response]
  (log (walk/keywordize-keys response)))

(defn zerodha-login []
  (log (str "User Zerodha" ))
    (go
      (read-login-response (<! (do-http-get ((:zerodha_login url_list))))))
  )

;;

;; web sockets
(comment)
(def unique-token (atom -1))
(def messages (atom {}))

(defn update-messages! [message]
;(defn update-messages! [{:keys [message]}]
  ;(.log js/console (str "in update-messages MESSAGE " (walk/keywordize-keys message)))
  (log (str "in update-messages MESSAGE " (:message message) (:token message)))
  (reset! unique-token (:token message))
  (swap! messages #(vec (take 10 (conj % (:message message))))))

(defonce ws-chan (atom nil))
(def json-reader (t/reader :json))
(def json-writer (t/writer :json-verbose))

(defn receive-transit-msg!
 [update-fn]
 (fn [msg]
   (log (str "Received - " (.-data msg)))
   (update-fn (->> msg .-data (t/read json-reader) (walk/keywordize-keys)))))

 (defn send-transit-msg!
 [msg]
 (log (str "SENDING ->\n" (t/write json-writer msg)))
 (if @ws-chan
        (.send @ws-chan (t/write json-writer msg))
   (throw (js/Error. "Websocket is not available!"))))

 (defn make-websocket! [url receive-handler]
 (println "attempting to connect websocket")
 (if-let [chan (js/WebSocket. url)]
   (do
     (set! (.-onmessage chan) (receive-transit-msg! receive-handler))
     (reset! ws-chan chan)
     (println "Websocket connection established with: " url))
   (throw (js/Error. "Websocket connection failed!"))))


;;

(def app-state (reagent/atom {
                        :person {:name "stoc"
                                 :password "..."
                                 :token ""}
                        :zerodha-user "ZERODHA-USER"
                        :ws-url ""
                        :base-strike-price 7900
                        :strike-price 8200 
                        :current-option 8100
                        :hedge-drawer false
                        :strategy-drawer false
                        :selected-strategy "buy-butterfly"
                        :span 100
                        :pending-orders {}
                        :saved-orders {}
                        :chart-config
                              {:chart {:type "line" :events {:click (fn [event] (print event)
                                )}}
                               :title {:text "Strategies v/s strike prices"}
                               :subtitle {:text "Source: Xpertview analytics"}
                               :xAxis {:categories ["7900" "8000" "8100" "8200" "8300" "8400" "8500" "8600" "8700" "8800" "8900"]
                                       :title {:text "Nifty"}}
                               :yAxis {:min -300
                                       :title {:text "Payoff -- ( Nifty points )"
                                               :align "high"}
                                       :labels {:overflow "justify"}}
                               :tooltip {:valueSuffix " rupees"}
                               :plotOptions {:bar {:dataLabels {:enabled true}}}
                               :legend {:layout "vertical"
                                        :align "right"
                                        :verticalAlign "top"
                                        :x 10
                                        :y 100
                                        :floating false
                                        :borderWidth 1
                                        :shadow true}
                               :credits {:enabled false}
                               :series [{:name "Long ATM Butterfly"
                                         :data [-10 -10 -10 90 -10 -10 -10 -10 -10 -10]}
                                        {:name "Short ATM Butterfly"
                                         :data [-10 -10 -10 90 -10 -10 -10 -10 -10 -10]}
                                        {:name "Long ATM Broken wing Butterfly"
                                         :data [-10 -10 -10 90 -10 -10 -10 -10 -10 -10]}
                                        {:name "Short ATM Broken wing Butterfly"
                                         :data [-10 -10 -10 90 -10 -10 -10 -10 -10 -10]}
                                        ]}
                        :my-chart-config
                              {:chart {:type "line" :events {:click (fn [event] (print event)
                                )}}
                               :title {:text "Strategies v/s strike prices"}
                               :subtitle {:text "Source: Xpertview analytics"}
                               :xAxis {:categories ["7900" "8000" "8100" "8200" "8300" "8400" "8500" "8600" "8700" "8800" "8900"]
                                       :title {:text "Nifty"}}
                               :yAxis {:min -300
                                       :title {:text "Payoff -- ( Nifty points )"
                                               :align "high"}
                                       :labels {:overflow "justify"}}
                               :tooltip {:valueSuffix " units"}
                               :plotOptions {:bar {:dataLabels {:enabled true}}}
                               :legend {:layout "vertical"
                                        :align "right"
                                        :verticalAlign "top"
                                        :x -40
                                        :y 100
                                        :floating false
                                        :borderWidth 1
                                        :shadow true}
                               :credits {:enabled false}
                               :series [{:name "Long ATM Butterfly"
                                         :data [-10 -10 -10 90 -10 -10 -10 -10 -10 -10]}
                                        {:name "Short ATM Butterfly"
                                         :data [-10 -10 -10 90 -10 -10 -10 -10 -10 -10]}
                                        {:name "Long ATM Broken wing Butterfly"
                                         :data [-10 -10 -10 90 -10 -10 -10 -10 -10 -10]}
                                        {:name "Short ATM Broken wing Butterfly"
                                         :data [-10 -10 -10 90 -10 -10 -10 -10 -10 -10]}
                                        ]}}))

(def strategy-guide {:long-atm-butterfly {
                          :header "Long ATM Butterfly" 
                          :detail (str "The butterfly spread is a neutral strategy that is a combination of a 
                                        bull spread and a bear spread. It is a limited profit, limited risk 
                                        options strategy. There are 3 striking prices involved in a butterfly 
                                        spread and it can be constructed using calls or puts.")
                          :orders [{:sp 0 :type "call" :order "buy" :qty 1 :pr 0}
                                   {:sp 1 :type "call" :order "sell" :qty 2 :pr 1}
                                   {:sp 2 :type "call" :order "buy" :qty 1 :pr 2}]}

                     :short-atm-butterfly {
                          :header "Short ATM Butterfly"
                          :detail "This is the converse of buying a butterfly spread"
                          :orders [{:sp 0 :type "call" :order "sell"  :qty 1 :pr 0}
                                   {:sp 1 :type "call" :order "buy" :qty 2 :pr 1}
                                   {:sp 2 :type "call" :order "sell" :qty 1 :pr 2}]}

                     :long-atm-bwb {
                          :header "Long ATM Broken Wing Butterfly"
                          :detail (str "In this strategy a short call spread is embedded inside a long call 
                            butterfly spread. A Broken Wing Butterfly is a long butterfly spread with 
                            long strikes that are not equidistant from the short strike. This leads to one 
                            side having greater risk than the other, which makes the trade slightly more 
                            directional than a standard long butterfly spread")
                          :orders [{:sp 0 :type "call" :order "buy" :qty 1 :pr 0}
                                   {:sp 2 :type "call" :order "sell" :qty 3 :pr 2}
                                   {:sp 3 :type "call" :order "buy" :qty 2 :pr 3}]}

                     :short-atm-bwb {
                          :header "Short ATM Broken Wing butterfly"
                          :detail "This is the converse of Buying a Broken wing butterfly"
                          :orders [{:sp 0 :type "call" :order "sell" :qty 1 :pr 0}
                                   {:sp 2 :type "call" :order "buy" :qty 3 :pr 2}
                                   {:sp 3 :type "call" :order "sell" :qty 2 :pr 3}]}
                     :long-atm-call-spread {
                          :header "Long ATM Call Spread"
                          :detail (str "A long call spread gives you the right to buy stock at strike price 1 and obligates you to sell the stock at strike price 2 if assigned")
                          :orders [{:sp 0 :type "Call" :order "Buy" :qty 1 :pr 0}
                                   {:sp 1 :type "Call" :order "Sell" :qty 1 :pr 1}]}
                     :long-atm-condor {
                          :header "Long ATM Condor Spread"
                          :detail (str "You can think of a long condor spread with calls as simultaneously running an in-the-money long call spread and an out-of-the-money short call spread. Ideally, you want the short call spread to expire worthless, while the long call spread achieves its maximum value with strikes 1 and 2 in-the-money")
                          :orders [{:sp 0 :type "call" :order "buy" :qty 1 :pr 0}
                                   {:sp 1 :type "call" :order "sell" :qty 1 :pr 1}
                                   {:sp 2 :type "call" :order "sell" :qty 1 :pr 2}
                                   {:sp 3 :type "call" :order "buy" :qty 1 :pr 3}
                                   ]}
                     :long-atm-call-ladder {
                          :header "Long ATM Call Ladder"
                          :detail (str "The call backspread (reverse call ratio spread) is a bullish 
                            strategy in options trading that involves selling a number of call options 
                            and buying more call options of the same underlying stock and expiration 
                            date at a higher strike price. It is an unlimited profit, limited risk options 
                            trading strategy that is taken when the options trader thinks that the 
                            underlying stock will experience significant upside movement in the near term.")
                          :orders [{:sp 0 :type "call" :order "sell" :qty 1 :pr 0}
                                   {:sp 1 :type "call" :order "buy" :qty 2 :pr 1}]}})

(defn matops []
  (let [a [1 2]
        b [3 4]]
    (mat/add a b)))

(def display-color "#dfe3ee")

(defn get-premium [strike-price]
  ;(print "Premiun for - : " strike-price)
  (let [bp (:base-strike-price @app-state)
        span (:span @app-state)]
  (cond 
        (= strike-price bp) 530
        (= strike-price (+ bp (* 1 span))) 430
        (= strike-price (+ bp (* 2 span))) 330
        (= strike-price (+ bp (* 3 span))) 230
        (= strike-price (+ bp (* 4 span))) 190
        (= strike-price (+ bp (* 5 span))) 120
        (= strike-price (+ bp (* 6 span))) 80
        (= strike-price (+ bp (* 7 span))) 50
        (= strike-price (+ bp (* 8 span))) 35
        (= strike-price (+ bp (* 9 span))) 15)))

(def stock-range (map #(+ (:base-strike-price @app-state) (* % (:span @app-state))) (range 10)))

(defn buy-call [strike-price premium]
  (map #(if (> strike-price %) 
            (- 0 premium) 
            (- % strike-price premium)) 
            stock-range))

(defn sell-call [strike-price premium]
  (map #(if (> strike-price %) 
            premium 
            (- strike-price % (- 0 premium))) 
            stock-range))

(defn buy-put [strike-price premium]
  (map #(if (< strike-price %) 
            (- 0 premium) 
            (- strike-price % premium)) 
            stock-range))

(defn sell-put [strike-price premium]
  (map #(if (< strike-price %) 
            premium 
            (- % strike-price (- 0 premium))) 
            stock-range))

(defn strategy-details [strategy-type]
  (fn []
    (let [sp1 (:strike-price @app-state)
            span (:span @app-state)
            sp2 (+ sp1 span)
            sp3 (+ sp1 (* 2 span))
            sp4 (+ sp1 (* 3 span))
            sp-vector [sp1 sp2 sp3 sp4]
            pr1 (get-premium sp1)
            pr2 (get-premium sp2)
            pr3 (get-premium sp3)
            pr4 (get-premium sp4)
            pr-vector [pr1 pr2 pr3 pr4]
            order-vector  (:orders ((keyword strategy-type) strategy-guide))]
      
      ;[rui/paper  {:zDepth 4 :style {:display "flex" :justify-content "space-around" :padding-left "10px" :flex-direction "row" :flex-flow "row wrap"}}
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
          [:div {:style {:flex "1"}}
            [:div {:style {:display "flex" :flex-direction "column" :flex-flow "column wrap"}}   
               (for [xt order-vector]
                ^{:key (str "v-" (:sp xt))}
                [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}}
                  [:div {:style {:flex "1"}} (get sp-vector (:sp xt))]
                  [:div {:style {:flex "1"}} (:type xt)]
                  [:div {:style {:flex "1"}} (:order xt)]
                  [:div {:style {:flex "1"}} (:qty xt) " lots"]
                  [:div {:style {:flex "1"}} (get pr-vector (:pr xt))]])]]])))

(defn execute-orders []
   (print "Executing " (:saved-orders @app-state))
   (loop [lx (:saved-orders @app-state)]
          (when (> (count lx) 0)
            (let [xt (first lx)]
            (print "Executing - " (get xt 1))
              (recur (rest lx)))))
  )

(defn xyz [xt]
  (let [order (:order (get xt 1))
      option (:option (get xt 1))
      qty (:qty (get xt 1)) 
      type (:type (get xt 1))]
  (match [type order]
    ["call" "buy"] (mat/mul qty (buy-call option (get-premium option)))
    ["call" "sell"] (mat/mul qty  (sell-call option (get-premium option)))
    ["put" "buy"] (mat/mul qty (buy-put option (get-premium option)))
    ["put" "sell"] (mat/mul qty  (sell-put option (get-premium option)))
    :else nil)))


(defn list-orders []
   (print "Listing " (:saved-orders @app-state))
   (loop [lx (:saved-orders @app-state)]
          (when (> (count lx) 0)
            (let [xt (first lx)]
            (print "Saved - " (get xt 1))
              (recur (rest lx)))))

   (loop [lx (:pending-orders @app-state)]
          (when (> (count lx) 0)
            (let [xt (first lx)]
            (print "Pending - " (get xt 1))
              (recur (rest lx)))))

   (print (map xyz (:saved-orders @app-state)))

   (print "Saved Orders " (reduce mat/add (into [] (map xyz (:saved-orders @app-state)))))

   (print (map xyz (:pending-orders @app-state)))
   (print "Pending Orders " (reduce mat/add (into [] (map xyz (:pending-orders @app-state)))))
   ;(reduce mat/add (into [] (map xyz (:pending-orders @app-state))))))
   ;(print (reduce mat/add (into [] (map xyz (:pending-orders @app-state)))))

  )

(defn execute-strategy [strategy-type])

(defn save-strategy [strategy-type]
  (let [sp1 (:strike-price @app-state)
        span (:span @app-state)
        sp2 (+ sp1 span)
        sp3 (+ sp1 (* 2 span))
        sp4 (+ sp1 (* 3 span))
        sp-vector [sp1 sp2 sp3 sp4]
        pr1 (get-premium sp1)
        pr2 (get-premium sp2)
        pr3 (get-premium sp3)
        pr4 (get-premium sp4)
        pr-vector [pr1 pr2 pr3 pr4]
        order-vector  (:orders ((keyword strategy-type) strategy-guide))]

        (loop [lx order-vector]
          (when (> (count lx) 0)
            (let [xt (first lx)]
            (swap! app-state assoc-in [:saved-orders (count (:saved-orders @app-state))] 
              {:id (rand-int 100) :order (:order xt) :qty (:qty xt) :option (get sp-vector (:sp xt)) :type (:type xt)})
            (recur (rest lx)))))
  ))

(defn strategy-card [strategy-type]
  (fn []
  (let [sp1 (:strike-price @app-state)
        span (:span @app-state)
        sp2 (+ sp1 span)
        sp3 (+ sp1 (* 2 span))
        pr1 (get-premium sp1)
        pr2 (get-premium sp2)
        pr3 (get-premium sp3)]
  [rui/card {:style {:padding "10px"}}
          [rui/card-header (:header ((keyword strategy-type) strategy-guide))]
          [rui/card-text (:detail ((keyword strategy-type) strategy-guide))]
          [(strategy-details strategy-type)]
          [rui/card-actions {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}}
              [:div {:style {:flex "1"} :on-click #(execute-strategy strategy-type)}  
                  [ui/raised-button {:label "Execute" :label-color "#FFFFFF" :background-color "#3b5998" :on-touch-tap #()}]]
              [:div {:style {:flex "1"}}
              ;:on-click #(save-strategy strategy-type)}  
                  [ui/raised-button {:label "Save" :label-color "#FFFFFF" :background-color "#3b5998" :on-touch-tap #(save-strategy strategy-type)}]]
              [:div {:style {:flex "1"}}  
                  [ui/raised-button {:label "Close" :label-color "#FFFFFF" :background-color "#3b5998"
                                     :on-touch-tap #(swap! app-state assoc-in [:strategy-drawer] false)}]]]])))


(defn nav-link [uri title page collapsed?]
  [:li.nav-item
   {:class (when (= page (session/get :page)) "active")}
   [:a.nav-link
    {:href uri
    :style {:color "#FFFFFF"}
     :on-click #(reset! collapsed? true)} title]])

(defn navbar []
  (let [collapsed? (reagent/atom true)]
    (fn []
      [:nav.navbar.navbar-light  {:style {:color "#FFFFFF"}}
       [:button.navbar-toggler.hidden-sm-up
        {:on-click #(swap! collapsed? not)} "☰"]
       [:div.collapse.navbar-toggleable-xs
        (when-not @collapsed? {:class "in"})
        [:a.navbar-brand {:href "#/"  :style {:color "#FFFFFF"}} "OptionSense"]
        [:ul.nav.navbar-nav
         [nav-link "#/" "Home" :home collapsed?]
         [nav-link "#/mystrategies" "My Strategies" :mystrategies collapsed?]
         [nav-link "#/recommendations" "Recommendations" :recommendations collapsed?]
         ;[nav-link "#/login" "Login" :recommendations collapsed?]
         [nav-link "/zerodha-login" "Login" :recommendations collapsed?]
         ;[nav-link (str "https://kite.trade/connect/login?api_key=" apikey) "Login - Zerodha" :recommendations collapsed?]
         [nav-link "#/about" "About" :about collapsed?]]]])))

(defn navbar-new []
  (fn []
    [rui/mui-theme-provider
      ;{:mui-theme (ui/get-mui-theme {:palette {:text-color (ui/color :blue500)}})}
      {:mui-theme (ui/get-mui-theme "darkBaseTheme")}
        [rui/toolbar
         [rui/toolbar-group 
            [rui/toolbar-title {:text "OptionSense"}]
         ]
         [rui/toolbar-group 
            [ui/raised-button {:label "Home" :on-touch-tap #()}]
            [ui/raised-button {:label "My Strategies" :on-touch-tap #()}]
            [ui/raised-button {:label "Recommendations" :on-touch-tap #()}]
         ]
        ]
        ]))

(defn about-page []
  (fn []
    [rui/mui-theme-provider
      {:mui-theme (ui/get-mui-theme {:palette {:text-color (ui/color :blue700)}})}
        [rui/paper  {:zDepth 1 :style {:display "flex" :justify-content "space-around" :flex-direction "column" :padding "10px" :flex-flow "column wrap"}}
           [:h6 "Developed by Xpertview analytics. This app is the culmination of personal experience in trading options in the Indian Stock Market."
]
           [:h6 "Various strategies suggested have been based on literature survey. More to come ..... in the exciting world of Option Trading!!!"]
]]))

(defn recommendations-page []
  (fn []
    [rui/mui-theme-provider
      {:mui-theme (ui/get-mui-theme {:palette {:text-color (ui/color :blue700)}})}
        [rui/paper  {:zDepth 1 :style {:display "flex" :justify-content "space-around" :flex-direction "column" :padding "10px" :flex-flow "column wrap"}}
           [:h6 "Recommended strategies based on your trading history and prevailing market conditions"]
           ]]))

(defn update-strike-price [strike-price]
  (let [strike-price (reader/read-string strike-price)
        sp1 strike-price
        span (:span @app-state)
        sp2 (+ sp1 span)
        sp3 (+ sp1 (* 2 span))
        sp4 (+ sp1 (* 3 span))
        pr1 (get-premium sp1)
        pr2 (get-premium sp2)
        pr3 (get-premium sp3)
        pr4 (get-premium sp4)]

        (def msg {:a "subscribe" :v [408065, 884737]})
        (send-transit-msg! msg)

  (swap! app-state assoc-in [:chart-config :series] [
    {:name "Long ATM Butterfly" 
     :data (mat/add (buy-call sp1 pr1) (mat/mul 2 (sell-call sp2 pr2)) (buy-call sp3 pr3))}
    {:name "Long ATM Call Ladder" 
     :data (mat/add (sell-call sp1 pr1) (mat/mul 2 (buy-call sp2 pr2)))}
    {:name "Short ATM Butterfly" 
     :data (mat/add (sell-call sp1 pr1) (mat/mul 2 (buy-call sp2 pr2)) (sell-call sp3 pr3))}
    {:name "Long ATM Broken Wing Butterfly" 
     :data (mat/add (buy-call sp1 pr1) (mat/mul 3 (sell-call sp3 pr3)) (mat/mul 2 (buy-call sp4 pr4)))}
    {:name "Long ATM Long Call Spread " 
     :data (mat/add (buy-call sp1 pr1) (sell-call sp2 pr2))}
    {:name "Long ATM Long Condor Spread " 
     :data (mat/add (buy-call sp1 pr1) (sell-call sp2 pr2) (sell-call sp3 pr3) (buy-call sp4 pr4))}
    {:name "Short ATM Broken Wing Butterfly"
     :data (mat/add (sell-call sp1 pr1) (mat/mul 3 (buy-call sp3 pr3)) (mat/mul 2 (sell-call sp4 pr4)))}
     {:name "My Strategy"
     ;:data (mat/add (reduce mat/add (into [] (map xyz (:saved-orders @app-state)))) (reduce mat/add (into [] (map xyz (:pending-orders @app-state)))))}
     :data (mat/add (reduce mat/add (map xyz (:saved-orders @app-state))) (reduce mat/add (map xyz (:pending-orders @app-state))))}
    ;{:name "Trading range histogram - 24 months" :data [25 7 90 10 120 200 110 90 68 26]}
 
    ])
  (swap! app-state assoc-in [:strike-price] strike-price)
 ; (print (@app-state :chart-config))
))


(defn highchart-render []
   (fn []
  (print "rendering chart" (@app-state :chart-config))
  [:div {:style {:min-width "310px" :max-width "800px"
                 :height "400px" :margin "0 auto"}}]))


(defn highchart-update [this]
  (print "Updating mount")
  (.highcharts (js/$ (reagent/dom-node this))
               (clj->js (:chart-config @app-state))))

(defn highchart-component []
  (reagent/create-class {:reagent-render highchart-render
                         :component-did-mount highchart-update
                         :component-did-update highchart-update
                        }))

(defn get-selected-strategy []
  (fn []  
    (let [template-def (:selected-strategy @app-state)]
      (print (str "template-def - " template-def))
      [(strategy-card template-def)])))

(defn strategy-dropdown []
  (fn []
    [rui/paper  {:zDepth 4 :style {:display "flex" :justify-content "space-around" :flex-direction "column" :flex-flow "column wrap"}}
        [rui/drop-down-menu {:value "" 
                            :on-change (fn [e index value] 
                                         (print (str "dropdown-click" e index value))
                                         (swap! app-state assoc-in [:selected-strategy] value))}
         [rui/menu-item {:value "" :primary-text "Select Strategy to execute"}]
         [rui/menu-item {:value "long-atm-butterfly" :primary-text "Long ATM Butterfly"}]
         [rui/menu-item {:value "short-atm-butterfly"} "Short ATM butterfly"]
         [rui/menu-item {:value "long-atm-call-ladder"} "Long ATM Call ladder"]
         [rui/menu-item {:value "long-atm-bwb"} "Long ATM Broken Wing butterfly"]
         [rui/menu-item {:value "short-atm-bwb"} "Short ATM Broken Wing butterfly"]
         [rui/menu-item {:value "long-atm-condor"} "Long ATM Condor Spread"]
         [rui/menu-item {:value "long-atm-call-spread"} "Long ATM Call Spread"]]]))

(defn strategy-drawer []
  (fn []
    [rui/drawer {:width 400 :open (:strategy-drawer @app-state) :open-secondary true}
        [:div {:style {:display "flex" :flex-direction "column" :flex-flow "column wrap"}}
          [:div {:style {:flex "1"}}  [strategy-dropdown] ]
          [:div {:style {:flex "1"}}  [:br]] 
          [:div {:style {:flex "1"}} [(get-selected-strategy)]]
        ]]))

(defn strategies-comp []
  (fn []
    [:div 
  [:div {:style {:display "flex" :flex-direction "column" :padding "10px" :flex-flow "column wrap"}}
    [:div {:style {:flex "1"}}
        [:label (str "Welcome " (:zerodha-user @app-state))]]
[:div {:style {:flex "1"}}
        [:label "Explore Option strategies at selected ATM Nifty Strike Price"]]

        ]
    [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}}
      [:div {:style {:flex 5}}
          ;[rui/paper  {:zDepth 4} 
          [:div {:style {:display "flex" :flex-direction "column" :flex-flow "column wrap"}}
          [:div {:style {:flex "1" :padding "10px"}} 
           ;[rui/paper  {:zDepth 4}
                  [rui/text-field
                          {:floatingLabelText "Enter ATM Nifty strike price eg: 8200"
                            :full-width true
                            :value (:strike-price @app-state)
                            :on-change #(update-strike-price (.. % -target -value))
                            }]
          ;[:div {:style {:flex "1" :padding "10px"}} 
            [rui/slider {:default-value 0.2
                         :step 0.1
                         :description "Or Use the Slider to select ATM Nifty Strike price"
                         :on-change (fn [e index value] 
                                         (print (str "slider-click" e index (type value)))
                                         (update-strike-price (str (+ (:base-strike-price @app-state)(* 1000 index))))
                                         )
                         }]
          ]
          [:div {:style {:flex "1"}} [highchart-component]]]
          ;]
         ]
      [:div {:style {:flex "0.5"}} 
                  [ui/raised-button {:label "Explore" 
                                     :label-color "#FFFFFF"
                                     :background-color "#3b5998"
                                     :on-touch-tap #(swap! app-state assoc-in [:strategy-drawer] true)}]]
      [:div {:style {:flex "1"}} [strategy-drawer]]]]))

(comment
(defn strategies-comp-old []
  (fn []
    [:div {:style {:display "flex" :flex-direction "row" :padding "10px" :flex-flow "row wrap"}}
      [:div {:style {:flex "1"}} 
          [:div {:style {:display "flex" :flex-direction "column" :padding "10px" :flex-flow "column wrap"}}
          [:div {:style {:flex "1"}} 
           [rui/paper  {:zDepth 4 :style {:display "flex" :justify-content "space-around" :flex-direction "column" :flex-flow "column wrap"}}
                [:h4 "Option Strategy screener for Nifty"]
                [:h4 "Choose a strike price and view the classical option strategies"]
                [:h4 "Note that the system picks up the last traded price of the options"]]]
          [:div {:style {:flex "1" :align-content "center"}} 
           [rui/paper  {:zDepth 4}
                  [rui/text-field
                          {
                          :floatingLabelText "Enter Nifty strike price eg: 8300"
                          :full-width false
                          :value (:strike-price @app-state)
                          :on-change #(update-strike-price (.. % -target -value))
                          }]]]
          [:div {:style {:flex "1"}} [highchart-component]]]]
    [:div {:style {:flex 1}}  [strategy-dropdown] ]
    [:div {:style {:flex "1"}} [(get-selected-strategy)]]
    [:div {:style {:flex "1"}} 
    [rui/tabs 
        [rui/tab {:label "Strategy Explorer"}
          [rui/paper  {:zDepth 4 :style {:display "flex" :justify-content "space-around" :flex-direction "column" :flex-flow "column wrap"}}
                 [:div {:style {:flex "1"}} [(buy-butterfly-card)]]
                 [:div {:style {:flex "1"}} [(sell-butterfly-card)]]
                 [:div {:style {:flex "1"}} [(buy-1-3-2-butterfly-card)]]
                 [:div {:style {:flex "1"}} [(sell-1-3-2-butterfly-card)]]

              ]]
        [rui/tab {:label "My Strategies"}
          [rui/paper  {:zDepth 4 :style {:display "flex" :justify-content "space-around" :flex-direction "column" :flex-flow "column wrap"}}
                 [:div {:style {:flex "1"}} "Executed Strategies"]
              ]]]]]))
)

(defn home-page []
  (let [name  (re-frame/subscribe [:name])] 
  (fn []
    [rui/mui-theme-provider
      {:mui-theme (ui/get-mui-theme {:palette {:text-color (ui/color :blue700)}})}
        [rui/paper  {:zDepth 4 :style {:display "flex" :justify-content "space-around" :flex-direction "column" :padding "10px" :flex-flow "column wrap"}}  
        [:div {:style {:flex "1"}}[strategies-comp]]]])))

(defn update-chart []
  (swap! app-state assoc-in [:chart-config :series] [
    {:name "My Strategy"
     :data (mat/add (reduce mat/add (map xyz (:saved-orders @app-state))) (reduce mat/add (map xyz (:pending-orders @app-state))))}]
  )
)

(defn delete-all-orders []
  (print "DELETING All")
      (swap! app-state assoc-in [:pending-orders] {})
      (swap! app-state assoc-in [:saved-orders] {})
  (update-chart))

(defn delete-pending-order [order]
  (print "DELETING " (:order (get order 1)) (:id (get order 1)))
  ;(print (map #(print (:id (get % 1))) (:pending-orders @app-state)))
  (let [curr-arr (filter #(not= (:id (get order 1)) (:id (get % 1))) (:pending-orders @app-state))]
    (print curr-arr)
    (if (= (count curr-arr) 0)
      (swap! app-state assoc-in [:pending-orders] {})
      (swap! app-state assoc-in [:pending-orders] curr-arr))
    (update-chart)))

(defn add-to-pending-orders [option option-type qty order-type]
  (swap! app-state assoc-in [:pending-orders (count (:pending-orders @app-state))] {:id (rand-int 100) :order order-type :option option :qty qty :type option-type})

  (update-chart))


(defn get-selected-order [xt]
  (let [sp1 (:strike-price @app-state)
        span (:span @app-state)
        sp2 (+ sp1 span)
        sp3 (+ sp1 (* 2 span))
        sp4 (+ sp1 (* 3 span))]
  (case xt
        "long-atm-call" {:option sp1 :type "call" :order "buy"}
        "long-otm-call" {:option sp2 :type "call" :order "buy"}
        "long-otm+1-call" {:option sp3 :type "call" :order "buy"}
        "long-otm+2-call" {:option sp4 :type "call" :order "buy"}
  )))



(defn hedge-dropdown []
  (fn []
    [rui/paper  {:zDepth 4 :style {:display "flex" :justify-content "space-around" :flex-direction "column" :flex-flow "column wrap"}}
        [rui/drop-down-menu {:value "" 
                            :on-change (fn [e index value] 
                                         (print (str "hedge dropdown-click" e index value))
                                         (print "Index " index)
                                         (print "Value " value)
                                         (let [xt  (get-selected-order value)]
                                         (add-to-pending-orders (:option xt) (:type xt) 1 (:order xt))))}

         [rui/menu-item {:value "" :primary-text "Select Strategy to hedge"}]
         [rui/menu-item {:value "long-atm-call" :primary-text "Long ATM call"}]
         [rui/menu-item {:value "long-otm-call" :primary-text "Long OTM call"}]
         [rui/menu-item {:value "long-otm+1-call" :primary-text "Long OTM+1 call"}]
         [rui/menu-item {:value "long-otm+2-call" :primary-text "Long OTM+2 call"}]

     ]]))


(defn option-selector []
  (fn []
    [:div {:style {:display "flex" :justify-content "center" :padding "10px" :flex-direction "column" :flex-flow "column wrap"}}
      [:div {:style {:flex "1"}} [hedge-dropdown]]
      
      [:div {:style {:flex "2"}}[:br]]
      [:div {:style {:flex "1"}}[:h6 "Pending Orders"]]
      [:div {:style {:flex "1"}} 
        [:ul
        (for [xt  (:pending-orders @app-state)]
          ^{:key (str "opt1 -" (rand-int 100))}
             [:li 
               [:div {:style {:flex "1"} :on-click #(delete-pending-order xt)} 
                        (str (:order (get xt 1)) " " (:option (get xt 1)) " " (:type (get xt 1))) 
                        [ui/icon-button {:tooltip "Delete order" :tooltip-position "bottom-right"}
                            (ic/action-delete)]]
             ])]
      [:div {:style {:flex "1"}}
        [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}}
        [:div {:style {:flex "1"} :on-click #()}  
                  [ui/raised-button {:label "Execute" :label-color "#FFFFFF" :background-color "#3b5998" :on-touch-tap #()}]]
        [:div {:style {:flex "1"}}  
                  [ui/raised-button {:label "Clear All" :label-color "#FFFFFF" :background-color "#3b5998"
                                     :on-touch-tap #(delete-all-orders)}]]
        [:div {:style {:flex "1"}}  
                  [ui/raised-button {:label "Close" :label-color "#FFFFFF" :background-color "#3b5998"
                                     :on-touch-tap #(swap! app-state assoc-in [:hedge-drawer] false)}]]
                  ]]]]))



(defn hedge-drawer []
  (fn []
    [rui/drawer {:width 400 :open (:hedge-drawer @app-state) :open-secondary true}
        [option-selector]]))


(defn option-selector-old []
  (fn []
    [:div {:style {:display "flex" :justify-content "center" :padding "10px" :flex-direction "column" :flex-flow "column wrap"}}
      [:h5 "Use the slider to select the hedge"]
      [:div {:style {:display "flex"  :flex-direction "row" :flex-flow "row wrap"}}
        [:div {:style {:flex "3"}}
           [rui/slider {:default-value 0.2
                         :step 0.1
                         :description (str "Selected Nifty Strike price " (:current-option @app-state))
                         :on-change (fn [e index value] 
                                         (print (str "slider-click" e index (type value)))
                                         (swap! app-state assoc-in [:current-option] 
                                          (+ (:base-strike-price @app-state)(* 1000 index)))
                                         )
                         }]]
        [:div {:style {:flex "2"}}
         [rui/radio-button-group {:name "option" 
                                  ;:default-selected "call"
                                  :on-change (fn [e index value] 
                                                 (print (str "radiobutton-click" e index value))
                                                 (swap! app-state assoc-in [:current-option-type] index))}
           [rui/radio-button {:value "call" :label "Call"}]
           [rui/radio-button {:value "put" :label "Put"}]
         ]]
         [:div {:style {:flex "2"}}
         [rui/radio-button-group {:name "order" 
                                  ;:default-selected "buy"
                                  :on-change (fn [e index value] 
                                                 (print (str "radiobutton-click" e index value))
                                                 (swap! app-state assoc-in [:current-order-type] index))}
           [rui/radio-button {:value "buy" :label "Buy"}]
           [rui/radio-button {:value "sell" :label "Sell"}]
         ]]

      [:div {:style {:flex "0.5"} :on-click #(add-to-pending-orders (:current-option @app-state) (:current-option-type @app-state) 1 (:current-order-type @app-state))}    
            [ui/icon-button {:tooltip "Add to Strategy" :tooltip-position "bottom-right"}
                        (ic/content-add-circle)]]]

      [:h6 "Pending Orders"]
      [:ul
        (for [xt  (:pending-orders @app-state)]
          ^{:key (str "opt1 -" (rand-int 100))}
             [:li 
               [:div {:style {:flex "1"} :on-click #(delete-pending-order xt)} 
                        (str (:order (get xt 1)) " " (:option (get xt 1)) " " (:type (get xt 1))) 
                        [ui/icon-button {:tooltip "Delete order" :tooltip-position "bottom-right"}
                            (ic/action-delete)]]
             ])]
        [:div {:style {:flex "1"} :on-click #()}  
                  [ui/raised-button {:label "Execute" :label-color "#FFFFFF" :background-color "#3b5998" :on-touch-tap #()}]]
        
        ]))



(defn mystrategies-page []
   (fn []
      [rui/mui-theme-provider
          {:mui-theme (ui/get-mui-theme {:palette {:text-color (ui/color :blue700)}})}
          ;{:mui-theme (ui/get-mui-theme "lightBaseTheme")}
            [rui/paper  {:zDepth 4 :style {:display "flex" :justify-content "space-around" :flex-direction "column" :padding "20px" :flex-flow "column wrap"}}
              [:div {:style {:flex "1"}}[:h6 "Saved and executed strategies\n"]]
              [:div {:style {:flex "1"}} [:h6 "Do a \"What If Hedge analysis \" to your positions before you execute a trade"]
]
              [:div {:style {:flex "1"}} 
                [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}}
                  [:div {:style {:flex "6"}}
                      ;[rui/paper  {:zDepth 4} 
                      ;[:div {:style {:display "flex" :flex-direction "column" :flex-flow "column wrap"}}
                        [highchart-component]]
                  [:div {:style {:flex "0.25"}} 
                    [ui/raised-button {:label "Hedge" :label-color "#FFFFFF" :background-color "#3b5998"
                                       :on-touch-tap #(swap! app-state assoc-in [:hedge-drawer] true)}]]
                  [:div {:style {:flex "1"}} [hedge-drawer]]]]]]))


(defn login-page []
  ;(let [login_doc (reagent/atom (@app-state :person))]
    (fn []
      [rui/mui-theme-provider
          {:mui-theme (ui/get-mui-theme {:palette {:text-color (ui/color :blue700)}})}
      [:form  {:className "form-horizontal"}
      [rui/paper  {:zDepth 0 :style {:display "flex" :align-items "center" :flex-direction "column" :padding "20px" :flex-flow "column wrap"}}
          [:div {:style {:flex "1"}}[:h6 "Enter your Zerodha credentials"]]
          [:div {:style {:flex "1"}} 
              [rui/text-field
                          {
                          :floatingLabelText "Enter UserID"
                          :full-width false
                          :value (:name (:person @app-state))
                          :on-change #(swap! app-state assoc-in [:person :name] (-> % .-target .-value))
                          }]]
          [:div {:style {:flex "1"}}
            [rui/text-field
                          {
                          :floatingLabelText "Enter Password"
                          :full-width false
                          :value (:password (:person @app-state))
                          :on-change #(swap! app-state assoc-in [:person :password] (-> % .-target .-value))
                          }]]
          [:div {:style {:flex "1"}}  
[:div {:style {:flex "0.25"}} 
                          [ui/raised-button {:label "LOGIN" 
                                             :on-touch-tap #(log (str (:name (:person @app-state))
                                                                      (:password (:person @app-state))
                                                                      ))}]]]]
                                           ]]))


(def pages
  {:home #'home-page
   :mystrategies #'mystrategies-page
   :login #'login-page
   :recommendations #'recommendations-page
   :about #'about-page})

(defn page []
  [(pages (session/get :page))])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :page :home))

(secretary/defroute "/login" []
  (session/put! :page :login))

(secretary/defroute "/about" []
  (session/put! :page :about))

(secretary/defroute "/mystrategies" []
  (session/put! :page :mystrategies))

(secretary/defroute "/recommendations" []
  (session/put! :page :recommendations))

(defn redirect_handle_page_load []
  (log "history event")
  (let [username (cookies/get "username")
        userid (cookies/get "user_id")
        public_token (cookies/get "publictoken")
        wsurl  (str "wss://websocket.kite.trade/?api_key=" apikey "&user_id=" userid "&public_token=" public_token)]
  (log (cookies/count))
  (log (cookies/keys))
  (log (str username " - " userid " - " public_token " - " wsurl))

  (if (cookies/contains-key? "publictoken")
      (make-websocket! wsurl update-messages!))
  
  (if (cookies/contains-key? "username")
      (swap! app-state assoc-in [:zerodha-user] (str (cookies/get "username"))))

  ))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
        (events/listen
          HistoryEventType/NAVIGATE
          (fn [event]
              (redirect_handle_page_load)
              (secretary/dispatch! (.-token event))))
        (.setEnabled true)))

;; -------------------------


;; Initialize app
(defn fetch-docs! []
  (GET (str js/context "/docs") {:handler #(session/put! :docs %)}))

(defn mount-components []
  (reagent/render [#'navbar] (.getElementById js/document "navbar"))
  (reagent/render [#'page] (.getElementById js/document "app")))

(defn init! []
  (re-frame/dispatch-sync [:initialize-db])
  (load-interceptors!)
  (fetch-docs!)
  (hook-browser-navigation!)
  (mount-components)
  ;(make-websocket! (str "ws://" (.-host js/location) "/ws") update-messages!)
  (update-strike-price (str (+ (:base-strike-price @app-state) (* 2 (:span @app-state)))))
  
  )
