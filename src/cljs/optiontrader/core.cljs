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

(def app-state (reagent/atom {:strike-price 7700 
                        :span 100
                        :chart-config
                              {:chart {:type "line" :events {:click (fn [event] (print event))}}
                               :title {:text "Strategies v/s strike prices"}
                               :subtitle {:text "Source: Xpertview analytics"}
                               :xAxis {:categories ["7500" "7600" "7700" "7800" "7900" "8000" "8100" "8200" "8300" "8400"]
                                       :title {:text "Nifty"}}
                               :yAxis {:min -200
                                       :title {:text "Payoff"
                                               :align "high"}
                                       :labels {:overflow "justify"}}
                               :tooltip {:valueSuffix " rupees"}
                               :plotOptions {:bar {:dataLabels {:enabled true}}}
                               :legend {:layout "vertical"
                                        :align "right"
                                        :verticalAlign "top"
                                        :x -40
                                        :y 100
                                        :floating true
                                        :borderWidth 1
                                        :shadow true}
                               :credits {:enabled false}
                               :series [{:name "Buy Butterfly"
                                         :data [-10 -10 -10 90 -10 -10 -10 -10 -10 -10]}
                                        {:name "Sell Butterfly"
                                         :data [-10 -10 -10 90 -10 -10 -10 -10 -10 -10]}
                                        {:name "Buy 1-3-2 Butterfly"
                                         :data [-10 -10 -10 90 -10 -10 -10 -10 -10 -10]}
                                        {:name "Sell 1-3-2 Butterfly"
                                         :data [-10 -10 -10 90 -10 -10 -10 -10 -10 -10]}
                                        ]}}))

(defn matops []
  (let [a [1 2]
        b [3 4]]
    (mat/add a b)))

(def display-color "#dfe3ee")

(defn get-premium [strike-price]
  ;(print "Premiun for - : " strike-price)
  (cond (= strike-price 7500) 230
        (= strike-price 7600) 190
        (= strike-price 7700) 120
        (= strike-price 7800) 80
        (= strike-price 7900) 50
        (= strike-price 8000) 35
        (= strike-price 8100) 15))

(def stock-range (map #(+ 7500 (* % 100)) (range 10)))

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

(defn buy-1-3-2-butterfly []
  (fn []
  (let [sp1 (:strike-price @app-state)
        span (:span @app-state)
        sp2 (+ sp1 span)
        sp3 (+ sp1 (* 2 span))
        sp4 (+ sp1 (* 3 span))
        pr1 (get-premium sp1)
        pr2 (get-premium sp2)
        pr3 (get-premium sp3)
        pr4 (get-premium sp4)]
  [rui/paper  {:zDepth 4 :style {:display "flex" :justify-content "space-around" :flex-direction "row" :flex-flow "row wrap"}}
      [:div {:style {:flex "1"}}
        [:div {:style {:display "flex" :flex-direction "column" :flex-flow "column wrap"}}
          [:div {:style {:flex "1"}} "Premium "]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp1]
            [:div {:style {:flex "1"}}  pr1]
          ]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp3]
            [:div {:style {:flex "1"}}  pr3]
          ]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp4]
            [:div {:style {:flex "1"}}  pr4]
          ]]]])))

(defn buy-1-3-2-butterfly-old []
  (fn []
  (let [sp1 (:strike-price @app-state)
        span (:span @app-state)
        sp2 (+ sp1 span)
        sp3 (+ sp1 (* 2 span))
        sp4 (+ sp1 (* 3 span))
        pr1 (get-premium sp1)
        pr2 (get-premium sp2)
        pr3 (get-premium sp3)
        pr4 (get-premium sp4)]
  [rui/paper  {:zDepth 4 :style {:display "flex" :justify-content "space-around" :flex-direction "row" :flex-flow "row wrap"}}
      [:div {:style {:flex "1"}}
        [:div {:style {:display "flex" :flex-direction "column" :flex-flow "column wrap"}}
          [:div {:style {:flex "1"}} "Sell 1-3-2 Butterfly  "]
          [:div {:style {:flex "1"}} "Premium "]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp1]
            [:div {:style {:flex "1"}}  pr1]
          ]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp3]
            [:div {:style {:flex "1"}}  pr3]
          ]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp4]
            [:div {:style {:flex "1"}}  pr4]
          ]]]
      [:div {:style {:flex "3"}}
      (str (mat/add (buy-call sp1 pr1) (mat/mul 3 (sell-call sp3 pr3)) (mat/mul 2 (buy-call sp4 pr4))))
      ]])))

(defn sell-1-3-2-butterfly []
  (fn []
  (let [sp1 (:strike-price @app-state)
        span (:span @app-state)
        sp2 (+ sp1 span)
        sp3 (+ sp1 (* 2 span))
        sp4 (+ sp1 (* 3 span))
        pr1 (get-premium sp1)
        pr2 (get-premium sp2)
        pr3 (get-premium sp3)
        pr4 (get-premium sp4)]
  [rui/paper  {:zDepth 4 :style {:display "flex" :justify-content "space-around" :flex-direction "row" :flex-flow "row wrap"}}
      [:div {:style {:flex "1"}}
        [:div {:style {:display "flex" :flex-direction "column" :flex-flow "column wrap"}}
          [:div {:style {:flex "1"}} "Premium "]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp1]
            [:div {:style {:flex "1"}}  pr1]
          ]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp3]
            [:div {:style {:flex "1"}}  pr3]
          ]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp4]
            [:div {:style {:flex "1"}}  pr4]
          ]]]])))

(defn sell-1-3-2-butterfly-old []
  (fn []
  (let [sp1 (:strike-price @app-state)
        span (:span @app-state)
        sp2 (+ sp1 span)
        sp3 (+ sp1 (* 2 span))
        sp4 (+ sp1 (* 3 span))
        pr1 (get-premium sp1)
        pr2 (get-premium sp2)
        pr3 (get-premium sp3)
        pr4 (get-premium sp4)]
  [rui/paper  {:zDepth 4 :style {:display "flex" :justify-content "space-around" :flex-direction "row" :flex-flow "row wrap"}}
      [:div {:style {:flex "1"}}
        [:div {:style {:display "flex" :flex-direction "column" :flex-flow "column wrap"}}
          [:div {:style {:flex "1"}} "Sell 1-3-2 Butterfly  "]
          [:div {:style {:flex "1"}} "Premium "]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp1]
            [:div {:style {:flex "1"}}  pr1]
          ]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp3]
            [:div {:style {:flex "1"}}  pr3]
          ]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp4]
            [:div {:style {:flex "1"}}  pr4]
          ]]]
      [:div {:style {:flex "3"}}
      (str (mat/add (sell-call sp1 pr1) (mat/mul 3 (buy-call sp3 pr3)) (mat/mul 2 (sell-call sp4 pr4))))
      ]])))

(defn sell-butterfly []
  (fn []
  (let [sp1 (:strike-price @app-state)
        span (:span @app-state)
        sp2 (+ sp1 span)
        sp3 (+ sp1 (* 2 span))
        pr1 (get-premium sp1)
        pr2 (get-premium sp2)
        pr3 (get-premium sp3)]
  [rui/paper  {:zDepth 4 :style {:display "flex" :justify-content "space-around" :flex-direction "row" :flex-flow "row wrap"}}
      [:div {:style {:flex "1"}}
        [:div {:style {:display "flex" :flex-direction "column" :flex-flow "column wrap"}}
          [:div {:style {:flex "1"}} "Premium "]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp1]
            [:div {:style {:flex "1"}}  pr1]
          ]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp2]
            [:div {:style {:flex "1"}}  pr2]
          ]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp3]
            [:div {:style {:flex "1"}}  pr3]
          ]]]])))

(defn sell-butterfly-old []
  (fn []
  (let [sp1 (:strike-price @app-state)
        span (:span @app-state)
        sp2 (+ sp1 span)
        sp3 (+ sp1 (* 2 span))
        pr1 (get-premium sp1)
        pr2 (get-premium sp2)
        pr3 (get-premium sp3)]
  [rui/paper  {:zDepth 4 :style {:display "flex" :justify-content "space-around" :flex-direction "row" :flex-flow "row wrap"}}
      [:div {:style {:flex "1"}}
        [:div {:style {:display "flex" :flex-direction "column" :flex-flow "column wrap"}}
          [:div {:style {:flex "1"}} "Sell Butterfly  "]
          [:div {:style {:flex "1"}} "Premium "]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp1]
            [:div {:style {:flex "1"}}  pr1]
          ]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp2]
            [:div {:style {:flex "1"}}  pr2]
          ]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp3]
            [:div {:style {:flex "1"}}  pr3]
          ]]]
      [:div {:style {:flex "3"}}
      (str (mat/add (sell-call sp1 pr1) (mat/mul 2 (buy-call sp2 pr2)) (sell-call sp3 pr3)))
      ]])))

(defn buy-butterfly-old []
  (fn []
  (let [sp1 (:strike-price @app-state)
        span (:span @app-state)
        sp2 (+ sp1 span)
        sp3 (+ sp1 (* 2 span))
        pr1 (get-premium sp1)
        pr2 (get-premium sp2)
        pr3 (get-premium sp3)]
  [rui/paper  {:zDepth 4 :style {:display "flex" :justify-content "space-around" :flex-direction "row" :flex-flow "row wrap"}}
      [:div {:style {:flex "1"}}
        [:div {:style {:display "flex" :flex-direction "column" :flex-flow "column wrap"}}
          [:div {:style {:flex "1"}} "Buy Butterfly  "]
          [:div {:style {:flex "1"}} "Premium "]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp1]
            [:div {:style {:flex "1"}}  pr1]
          ]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp2]
            [:div {:style {:flex "1"}}  pr2]
          ]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp3]
            [:div {:style {:flex "1"}}  pr3]
          ]]]
      [:div {:style {:flex "3"}}
      (str (mat/add (buy-call sp1 pr1) (mat/mul 2 (sell-call sp2 pr2)) (buy-call sp3 pr3)))
      ]])))

(defn buy-butterfly []
  (fn []
  (let [sp1 (:strike-price @app-state)
        span (:span @app-state)
        sp2 (+ sp1 span)
        sp3 (+ sp1 (* 2 span))
        pr1 (get-premium sp1)
        pr2 (get-premium sp2)
        pr3 (get-premium sp3)]
  [rui/paper  {:zDepth 4 :style {:display "flex" :justify-content "space-around" :flex-direction "row" :flex-flow "row wrap"}}
      [:div {:style {:flex "1"}}
        [:div {:style {:display "flex" :flex-direction "column" :flex-flow "column wrap"}}
          [:div {:style {:flex "1"}} "Premium "]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp1]
            [:div {:style {:flex "1"}}  pr1]
          ]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp2]
            [:div {:style {:flex "1"}}  pr2]
          ]
          [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}} 
            [:div {:style {:flex "1"}}  sp3]
            [:div {:style {:flex "1"}}  pr3]
          ]]]])))

(defn buy-butterfly-card []
  (fn []
  (let [sp1 (:strike-price @app-state)
        span (:span @app-state)
        sp2 (+ sp1 span)
        sp3 (+ sp1 (* 2 span))
        pr1 (get-premium sp1)
        pr2 (get-premium sp2)
        pr3 (get-premium sp3)]
  [rui/card 
          [rui/card-header "Buy Butterfly"]
          [rui/card-text "Details for this strategy"]
          [buy-butterfly]
          [rui/card-actions {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}}
              [:div {:style {:flex "1"} :on-click #()}  
                  [ui/icon-button {:tooltip "Execute " :tooltip-position "bottom-right"}
                          (ic/content-send)]]
              [:div {:style {:flex "1"} :on-click #()}  
                  [ui/icon-button {:tooltip "Save " :tooltip-position "bottom-right"}
                          (ic/content-save)]]
                          ]])))


(defn sell-butterfly-card []
  (fn []
  (let [sp1 (:strike-price @app-state)
        span (:span @app-state)
        sp2 (+ sp1 span)
        sp3 (+ sp1 (* 2 span))
        pr1 (get-premium sp1)
        pr2 (get-premium sp2)
        pr3 (get-premium sp3)]
  [rui/card 
          [rui/card-header "Sell Butterfly"]
          [rui/card-text "Details for this strategy"]
          [sell-butterfly]
          [rui/card-actions {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}}
              [:div {:style {:flex "1"} :on-click #()}  
                  [ui/icon-button {:tooltip "Execute " :tooltip-position "bottom-right"}
                          (ic/content-send)]]
              [:div {:style {:flex "1"} :on-click #()}  
                  [ui/icon-button {:tooltip "Save " :tooltip-position "bottom-right"}
                          (ic/content-save)]]
                          ]])))

(defn sell-1-3-2-butterfly-card []
  (fn []
  (let [sp1 (:strike-price @app-state)
        span (:span @app-state)
        sp2 (+ sp1 span)
        sp3 (+ sp1 (* 2 span))
        pr1 (get-premium sp1)
        pr2 (get-premium sp2)
        pr3 (get-premium sp3)]

  [rui/card 
          [rui/card-header "Sell 1-3-2 Butterfly"]
          [rui/card-text "Details for this strategy"]
          [sell-1-3-2-butterfly]
          [rui/card-actions {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}}
              [:div {:style {:flex "1"} :on-click #()}  
                  [ui/icon-button {:tooltip "Execute " :tooltip-position "bottom-right"}
                          (ic/content-send)]]
              [:div {:style {:flex "1"} :on-click #()}  
                  [ui/icon-button {:tooltip "Save " :tooltip-position "bottom-right"}
                          (ic/content-save)]]
                          ]])))

(defn buy-1-3-2-butterfly-card []
  (fn []
  (let [sp1 (:strike-price @app-state)
        span (:span @app-state)
        sp2 (+ sp1 span)
        sp3 (+ sp1 (* 2 span))
        pr1 (get-premium sp1)
        pr2 (get-premium sp2)
        pr3 (get-premium sp3)]

  [rui/card 
          [rui/card-header "Buy 1-3-2 Butterfly"]
          [rui/card-text "Details for this strategy"]
          [buy-1-3-2-butterfly]
          [rui/card-actions {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}}
              [:div {:style {:flex "1"} :on-click #()}  
                  [ui/icon-button {:tooltip "Execute " :tooltip-position "bottom-right"}
                          (ic/content-send)]]
              [:div {:style {:flex "1"} :on-click #()}  
                  [ui/icon-button {:tooltip "Save " :tooltip-position "bottom-right"}
                          (ic/content-save)]]
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
      [:nav.navbar.navbar-light.bg-faded
       [:button.navbar-toggler.hidden-sm-up
        {:on-click #(swap! collapsed? not)} "☰"]
       [:div.collapse.navbar-toggleable-xs
        (when-not @collapsed? {:class "in"})
        [:a.navbar-brand {:href "#/"} "optiontrader"]
        [:ul.nav.navbar-nav
         [nav-link "#/" "Home" :home collapsed?]
         [nav-link "#/about" "About" :about collapsed?]]]])))

(defn about-page []
  [:div.container
   [:div.row
    [:div.col-md-12
     (str "this is the story of optiontrader. More to come") ]]])

(defn home-page-old []
  [:div.container
   [:div.jumbotron
    [:h1 "Welcome to optiontrader"]
    [:p "Time to start building your site!"]
    [:p [:a.btn.btn-primary.btn-lg {:href "http://luminusweb.net"} "Learn more »"]]]
   [:div.row
    [:div.col-md-12
     [:h2 "Welcome to ClojureScript"]]]
   (when-let [docs (session/get :docs)]
     [:div.row
      [:div.col-md-12
       [:div {:dangerouslySetInnerHTML
              {:__html (md->html docs)}}]]])])

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
    {:name "Sell Butterfly" 
     :data (mat/add (sell-call sp1 pr1) (mat/mul 2 (buy-call sp2 pr2)) (sell-call sp3 pr3))}
    {:name "Buy 1-3-2 Butterfly" 
     :data (mat/add (buy-call sp1 pr1) (mat/mul 3 (sell-call sp3 pr3)) (mat/mul 2 (buy-call sp4 pr4)))}
    {:name "Sell 1-3-2 Butterfly"
     :data (mat/add (sell-call sp1 pr1) (mat/mul 3 (buy-call sp3 pr3)) (mat/mul 2 (sell-call sp4 pr4)))}
 
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
    (cond (= template-def "") [buy-butterfly-card]
              (= template-def "buy-butterfly") [buy-butterfly-card]
              (= template-def "sell-butterfly") [sell-butterfly-card]
              (= template-def "buy-1-3-2-butterfly") [buy-1-3-2-butterfly-card]
              (= template-def "sell-1-3-2-butterfly") [sell-1-3-2-butterfly-card]))))

(defn strategy-dropdown []
  (fn []
    [rui/paper  {:zDepth 4 :style {:display "flex" :justify-content "space-around" :flex-direction "column" :flex-flow "column wrap"}}
        [rui/drop-down-menu {:value "" 
        :on-change (fn [e index value] 
                     (print (str "dropdown-click" e index value))

        (cond (= value "buy-butterfly") (swap! app-state assoc-in [:selected-strategy] value)
        (= value "sell-butterfly") (swap! app-state assoc-in [:selected-strategy] value)
        (= value "buy-1-3-2-butterfly") (swap! app-state assoc-in [:selected-strategy] value)
        (= value "sell-1-3-2-butterfly") (swap! app-state assoc-in [:selected-strategy] value))
                                                       )}
         [rui/menu-item {:value "" :primary-text"Select Strategy to execute"}]
         [rui/menu-item {:value "buy-butterfly" :primary-text "Buy Butterfly"}]
         [rui/menu-item {:value "sell-butterfly"} "Sell butterfly"]
         [rui/menu-item {:value "buy-1-3-2-butterfly"} "Buy 1-3-2 butterfly"]
         [rui/menu-item {:value "sell-1-3-2-butterfly"} "Sell 1-3-2 butterfly"]]]))

(defn strategies-comp []
  (fn []
    [:div {:style {:display "flex" :flex-direction "row" :flex-flow "row wrap"}}
      [:div {:style {:flex "1"}} 
          [:div {:style {:display "flex" :flex-direction "column" :flex-flow "column wrap"}}
          [:div {:style {:flex "1"}} 
           [rui/paper  {:zDepth 4 :style {:display "flex" :justify-content "space-around" :flex-direction "column" :flex-flow "column wrap"}}
                [:h5 "This is a tool to view classical option strategies for Nifty"]
                [:h5 "please enter the strike price "]]]
          [:div {:style {:flex "1"}} 
           [rui/paper  {:zDepth 4}
                  [rui/text-field
                          {:floatingLabelText "Enter Nifty strike price eg: 7500"
                            :full-width false
                            :value (:strike-price @app-state)
                            :on-change #(update-strike-price (.. % -target -value))
                            }]]]
          [:div {:style {:flex "1"}} [highchart-component]]]]
      [:div {:style {:flex "1"}} 
        [:div {:style {:display "flex" :flex-direction "column" :flex-flow "column wrap"}}
          [:div {:style {:flex "1"}}  [strategy-dropdown] ]
          [:div {:style {:flex "1"}} [(get-selected-strategy)]]
        ]]]))

(defn strategies-comp-old []
  (fn []
    [:div {:style {:display "flex" :flex-direction "row" :padding "10px" :flex-flow "row wrap"}}
      [:div {:style {:flex "1"}} 
          [:div {:style {:display "flex" :flex-direction "column" :padding "10px" :flex-flow "column wrap"}}
          [:div {:style {:flex "1"}} 
           [rui/paper  {:zDepth 4 :style {:display "flex" :justify-content "space-around" :flex-direction "column" :flex-flow "column wrap"}}
                [:h4 "This is a tool to identify standard strategies to trade in Nifty"]
                [:h4 "please enter the strike price "]]]
          [:div {:style {:flex "1"}} 
           [rui/paper  {:zDepth 4}
                  [rui/text-field
                          {
                          :floatingLabelText "Enter Nifty strike price eg: 7500"
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

(defn home-page []
  (fn []
    [rui/mui-theme-provider
      {:mui-theme (ui/get-mui-theme {:palette {:text-color (ui/color :blue500)}})}
        [strategies-comp]]))

(def pages
  {:home #'home-page
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
  (mount-components))
