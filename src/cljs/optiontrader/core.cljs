(ns optiontrader.core
  (:require [reagent.core :as reagent]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [clojure.core.matrix :as mat]
            [goog.history.EventType :as HistoryEventType]
            [markdown.core :refer [md->html]]
            [optiontrader.ajax :refer [load-interceptors!]]
            [ajax.core :refer [GET POST]]
            [cljs.reader :as reader]
            [cljsjs.highcharts :as highcharts]
            [cljsjs.material-ui]
            [cljs-react-material-ui.core :as ui]
            [cljs-react-material-ui.reagent :as rui]
            [cljs-react-material-ui.icons :as ic])
  (:import goog.History))

(def app-state (reagent/atom {:base-strike-price 7900
                        :strike-price 8200 
                        :current-option 8100
                        :selected-strategy "buy-butterfly"
                        :span 100
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
                                        :x -40
                                        :y 100
                                        :floating false
                                        :borderWidth 1
                                        :shadow true}
                               :credits {:enabled false}
                               :series [{:name "Buy Butterfly"
                                         :data [-10 -10 -10 90 -10 -10 -10 -10 -10 -10]}
                                        {:name "Sell Butterfly"
                                         :data [-10 -10 -10 90 -10 -10 -10 -10 -10 -10]}
                                        {:name "Buy Broken wing Butterfly"
                                         :data [-10 -10 -10 90 -10 -10 -10 -10 -10 -10]}
                                        {:name "Sell Broken wing Butterfly"
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
                               :series [{:name "Buy Butterfly"
                                         :data [-10 -10 -10 90 -10 -10 -10 -10 -10 -10]}
                                        {:name "Sell Butterfly"
                                         :data [-10 -10 -10 90 -10 -10 -10 -10 -10 -10]}
                                        {:name "Buy Broken wing Butterfly"
                                         :data [-10 -10 -10 90 -10 -10 -10 -10 -10 -10]}
                                        {:name "Sell Broken wing Butterfly"
                                         :data [-10 -10 -10 90 -10 -10 -10 -10 -10 -10]}
                                        ]}}))

(def strategy-guide {:buy-butterfly {
                          :header "Buy Butterfly" 
                          :detail (str "The butterfly spread is a neutral strategy that is a combination of a 
                                        bull spread and a bear spread. It is a limited profit, limited risk 
                                        options strategy. There are 3 striking prices involved in a butterfly 
                                        spread and it can be constructed using calls or puts.")
                          :orders [{:sp 0 :type "Call" :order "Buy 1 Lots" :pr 0}
                                   {:sp 1 :type "Call" :order "Sell 2 Lots" :pr 1}
                                   {:sp 2 :type "Call" :order "Buy 1 Lots" :pr 2}]}

                     :sell-butterfly {
                          :header "Sell Butterfly"
                          :detail "This is the converse of buying a butterfly spread"
                          :orders [{:sp 0 :type "Call" :order "Sell 1 Lots" :pr 0}
                                   {:sp 1 :type "Call" :order "Buy 2 Lots" :pr 1}
                                   {:sp 2 :type "Call" :order "Sell 1 Lots" :pr 2}]}

                     :buy-broken-wing-butterfly {
                          :header "Buy Broken Wing Butterfly"
                          :detail (str "In this strategy a short call spread is embedded inside a long call 
                            butterfly spread. A Broken Wing Butterfly is a long butterfly spread with 
                            long strikes that are not equidistant from the short strike. This leads to one 
                            side having greater risk than the other, which makes the trade slightly more 
                            directional than a standard long butterfly spread")
                          :orders [{:sp 0 :type "Call" :order "Buy 1 Lots" :pr 0}
                                   {:sp 2 :type "Call" :order "Sell 3 Lots" :pr 2}
                                   {:sp 3 :type "Call" :order "Buy 2 Lots" :pr 3}]}

                     :sell-broken-wing-butterfly {
                          :header "Sell Broken Wing butterfly"
                          :detail "This is the converse of Buying a Broken wing butterfly"
                          :orders [{:sp 0 :type "Call" :order "Sell 1 Lots" :pr 0}
                                   {:sp 2 :type "Call" :order "Buy 3 Lots" :pr 2}
                                   {:sp 3 :type "Call" :order "Sell 2 Lots" :pr 3}]}
                     :long-call-spread {
                          :header "Long Call Spread"
                          :detail (str "A long call spread gives you the right to buy stock at strike price 1 and obligates you to sell the stock at strike price 2 if assigned")
                          :orders [{:sp 0 :type "Call" :order "Buy 1 Lots" :pr 0}
                                   {:sp 1 :type "Call" :order "Sell 1 Lots" :pr 1}]}
                     :long-condor-spread {
                          :header "Long Condor Spread"
                          :detail (str "You can think of a long condor spread with calls as simultaneously running an in-the-money long call spread and an out-of-the-money short call spread. Ideally, you want the short call spread to expire worthless, while the long call spread achieves its maximum value with strikes 1 and 2 in-the-money")
                          :orders [{:sp 0 :type "Call" :order "Buy 1 Lots" :pr 0}
                                   {:sp 1 :type "Call" :order "Sell 1 Lots" :pr 1}
                                   {:sp 2 :type "Call" :order "Sell 1 Lots" :pr 2}
                                   {:sp 3 :type "Call" :order "Buy 1 Lots" :pr 3}
                                   ]}
                     :buy-call-ladder {
                          :header "Buy Call Ladder"
                          :detail (str "The call backspread (reverse call ratio spread) is a bullish 
                            strategy in options trading that involves selling a number of call options 
                            and buying more call options of the same underlying stock and expiration 
                            date at a higher strike price. It is an unlimited profit, limited risk options 
                            trading strategy that is taken when the options trader thinks that the 
                            underlying stock will experience significant upside movement in the near term.")
                          :orders [{:sp 0 :type "Call" :order "Sell 1 Lots" :pr 0}
                                   {:sp 1 :type "Call" :order "Buy 2 Lots" :pr 1}]}})

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
  
  [rui/paper  {:zDepth 4 :style {:display "flex" :justify-content "space-around" :padding-left "10px" :flex-direction "row" :flex-flow "row wrap"}}
      [:div {:style {:flex "1"}}
        [:div {:style {:display "flex" :flex-direction "column" :flex-flow "column wrap"}}   
           (for [xt order-vector]
            ^{:key (str "v-" (:sp xt))}
            [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}}
              [:div {:style {:flex "1"}} (get sp-vector (:sp xt))]
              [:div {:style {:flex "1"}} (:type xt)]
              [:div {:style {:flex "1"}} (:order xt)]
              [:div {:style {:flex "1"}} (get pr-vector (:pr xt))]]
            )]]])))

(defn strategy-card [strategy-type]
  (fn []
  (let [sp1 (:strike-price @app-state)
        span (:span @app-state)
        sp2 (+ sp1 span)
        sp3 (+ sp1 (* 2 span))
        pr1 (get-premium sp1)
        pr2 (get-premium sp2)
        pr3 (get-premium sp3)]
  [rui/card 
          [rui/card-header (:header ((keyword strategy-type) strategy-guide))]
          [rui/card-text (:detail ((keyword strategy-type) strategy-guide))]
          [(strategy-details strategy-type)]
          [rui/card-actions {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}}
              [:div {:style {:flex "1"} :on-click #()}  
                  [ui/raised-button {:label        "Execute"
                        :icon         (ic/content-send)
                        :on-touch-tap #()}]]
              [:div {:style {:flex "1"} :on-click #()}  
                  [ui/raised-button {:label        "Save"
                        :icon         (ic/content-save)
                        :on-touch-tap #()}]]
                          ]])))


(defn nav-link [uri title page collapsed?]
  [:li.nav-item
   {:class (when (= page (session/get :page)) "active")}
   [:a.nav-link
    {:href uri
     :on-click #(reset! collapsed? true)} title]])

(defn navbar []
  (let [collapsed? (reagent/atom true)]
    (fn []
      [:nav.navbar.navbar-light
       [:button.navbar-toggler.hidden-sm-up
        {:on-click #(swap! collapsed? not)} "☰"]
       [:div.collapse.navbar-toggleable-xs
        (when-not @collapsed? {:class "in"})
        [:a.navbar-brand {:href "#/"} "OptionsLab"]
        [:ul.nav.navbar-nav
         [nav-link "#/" "Home" :home collapsed?]
         [nav-link "#/mystrategies" "My Strategies" :mystrategies collapsed?]
         [nav-link "#/recommendations" "Recommendations" :recommendations collapsed?]
         [nav-link "#/about" "About" :about collapsed?]]]])))

(defn about-page []
  [:div.container
   [:div.row
    [:div.col-md-12
     "Developed by Xpertview analytics. This app is the culmination of personal experience in trading options in the Indian Stock Market."]]
  [:div.row
    [:div.col-md-12
      "Various strategies suggested have been based on literature survey. More to come ..... in the exciting world of Option Trading!!!" ]]])

(defn recommendations-page []
  [:div.container
   [:div.row
    [:div.col-md-12
     (str "Recommended strategies based on your trading history and prevailing market conditions") ]]])




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

  (swap! app-state assoc-in [:chart-config :series] [
    {:name "Buy Butterfly" 
     :data (mat/add (buy-call sp1 pr1) (mat/mul 2 (sell-call sp2 pr2)) (buy-call sp3 pr3))}
    {:name "Buy Call Ladder" 
     :data (mat/add (sell-call sp1 pr1) (mat/mul 2 (buy-call sp2 pr2)))}
    {:name "Sell Butterfly" 
     :data (mat/add (sell-call sp1 pr1) (mat/mul 2 (buy-call sp2 pr2)) (sell-call sp3 pr3))}
    {:name "Buy Broken Wing Butterfly" 
     :data (mat/add (buy-call sp1 pr1) (mat/mul 3 (sell-call sp3 pr3)) (mat/mul 2 (buy-call sp4 pr4)))}
    {:name "Buy Long Call Spread " 
     :data (mat/add (buy-call sp1 pr1) (sell-call sp2 pr2))}
    {:name "Buy Long Condor Spread " 
     :data (mat/add (buy-call sp1 pr1) (sell-call sp2 pr2) (sell-call sp3 pr3) (buy-call sp4 pr4))}
    {:name "Sell Broken Wing Butterfly"
     :data (mat/add (sell-call sp1 pr1) (mat/mul 3 (buy-call sp3 pr3)) (mat/mul 2 (sell-call sp4 pr4)))}
    ;{:name "Trading range histogram - 24 months" :data [25 7 90 10 120 200 110 90 68 26]}
 
    ])
  (swap! app-state assoc-in [:strike-price] strike-price)
  (print (@app-state :chart-config))
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
         [rui/menu-item {:value "" :primary-text"Select Strategy to execute"}]
         [rui/menu-item {:value "buy-butterfly" :primary-text "Buy Butterfly"}]
         [rui/menu-item {:value "sell-butterfly"} "Sell butterfly"]
         [rui/menu-item {:value "buy-call-ladder"} "Buy Call ladder"]
         [rui/menu-item {:value "buy-broken-wing-butterfly"} "Buy Broken Wing butterfly"]
         [rui/menu-item {:value "sell-broken-wing-butterfly"} "Sell Broken Wing butterfly"]
         [rui/menu-item {:value "long-condor-spread"} "Long Condor Spread"]
         [rui/menu-item {:value "long-call-spread"} "Long Call Spread"]]]))

(defn strategies-comp []
  (fn []
    [:div 
  [rui/paper  {:zDepth 4 :style {:display "flex" :justify-content "space-around" :flex-direction "column" :padding "10px" :flex-flow "column wrap"}}
    [:h5 "Explore Option strategies at selected Nifty Strike Price"]]
    [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}}
      [:div {:style {:flex "2"}}
          [rui/paper  {:zDepth 4} 
          [:div {:style {:display "flex" :flex-direction "column" :flex-flow "column wrap"}}
          [:div {:style {:flex "1" :padding "10px"}} 
           ;[rui/paper  {:zDepth 4}
                  [rui/text-field
                          {:floatingLabelText "Enter Nifty strike price eg: 8200"
                            :full-width false
                            :value (:strike-price @app-state)
                            :on-change #(update-strike-price (.. % -target -value))
                            }]
          ;[:div {:style {:flex "1" :padding "10px"}} 
            [rui/slider {:default-value 0.2
                         :step 0.1
                         :description "Or Use the Slider to select Nifty Strike price"
                         :on-change (fn [e index value] 
                                         (print (str "slider-click" e index (type value)))
                                         (update-strike-price (str (+ (:base-strike-price @app-state)(* 1000 index))))
                                         )
                         }]
          ]
          [:div {:style {:flex "1"}} [highchart-component]]]]]
      [:div {:style {:flex "1"}} 
        [:div {:style {:display "flex" :flex-direction "column" :flex-flow "column wrap"}}
          [:div {:style {:flex "1"}}  [strategy-dropdown] ]
          [:div {:style {:flex "1"}}  [:br]] 
          [:div {:style {:flex "1"}} [(get-selected-strategy)]]
        ]]]]))

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
                 ;[:div {:style {:flex "1"}} [(buy-1-3-2-butterfly)]]
                 ;[:div {:style {:flex "1"}} [(sell-1-3-2-butterfly)]]
                 ;[:div {:style {:flex "1"}} [(sell-butterfly)]]
                 ;[:div {:style {:flex "1"}} [(buy-butterfly)]]
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
  (fn []
    [rui/mui-theme-provider
      {:mui-theme (ui/get-mui-theme {:palette {:text-color (ui/color :blue500)}})}
        [strategies-comp]]))

(defn option-selector []
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
                                  :default-selected "call"
                                  :on-change (fn [e index value] 
                                                 (print (str "radiobutton-click" e index value))
                                                 (swap! app-state assoc-in [:current-option-type] index))}
           [rui/radio-button {:value "call" :label "Call"}]
           [rui/radio-button {:value "put" :label "Put"}]
         ]]
         [:div {:style {:flex "2"}}
         [rui/radio-button-group {:name "order" 
                                  :default-selected "buy"
                                  :on-change (fn [e index value] 
                                                 (print (str "radiobutton-click" e index value))
                                                 (swap! app-state assoc-in [:current-order-type] index))}
           [rui/radio-button {:value "buy" :label "Buy"}]
           [rui/radio-button {:value "sell" :label "Sell"}]
         ]]

      [:div {:style {:flex "0.5"} :on-click #(print (:current-option @app-state))}    
            [ui/icon-button {:tooltip "Add to Strategy" :tooltip-position "bottom-right"}
                        (ic/content-add-circle)]]]
(comment
      [:ul
        (for [bp activity-bp-list]
          ^{:key (str "bp2 -" (:id bp))}
             [:li 
               [:div {:style {:flex "1"} :on-click #()} 
                        (:bullet-point bp) [:span "  "]
                        [ui/icon-button {:tooltip "Delete selected bullet point" :tooltip-position "bottom-right"}
                            (ic/action-delete)]]
             ])]
        )
        ]))



(defn mystrategies-page []
   (fn []
      [rui/mui-theme-provider
          {:mui-theme (ui/get-mui-theme {:palette {:text-color (ui/color :blue500)}})}
            [rui/paper  {:zDepth 4 :style {:display "flex" :justify-content "space-around" :flex-direction "column" :padding "10px" :flex-flow "column wrap"}}
              [:h5 "View all your saved and executed strategies\n"]
              [:h5 "You can do a \"What If Hedge analysis \" to your positions and execute a trade"]

              [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}}
                [:div {:style {:flex "2"}}
                    [rui/paper  {:zDepth 4} 
                    [:div {:style {:display "flex" :flex-direction "column" :flex-flow "column wrap"}}
                    [:div {:style {:flex "1"}} [highchart-component]]]]]
                    [:div {:style {:flex "1"}}
                    [rui/paper  {:zDepth 4}
                      [option-selector]]]]]]))

(def pages
  {:home #'home-page
   :mystrategies #'mystrategies-page
   :recommendations #'recommendations-page
   :about #'about-page})

(defn page []
  [(pages (session/get :page))])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :page :home))

(secretary/defroute "/about" []
  (session/put! :page :about))

(secretary/defroute "/mystrategies" []
  (session/put! :page :mystrategies))

(secretary/defroute "/recommendations" []
  (session/put! :page :recommendations))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
        (events/listen
          HistoryEventType/NAVIGATE
          (fn [event]
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
  (load-interceptors!)
  (fetch-docs!)
  (hook-browser-navigation!)
  (mount-components)
  (update-strike-price (str (+ (:base-strike-price @app-state) (* 2 (:span @app-state))))))
