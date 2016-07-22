(ns optiontrader.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]))

(defn log [s]
  (.log js/console (str s))
  )

(re-frame/register-sub
 :name
 (fn [db]
 	(log (str "subs name " (:name @db)))
   (reaction (:name @db))))