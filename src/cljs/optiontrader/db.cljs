(ns optiontrader.db
	(:require [datascript.core :as d]
		      [reagent.core :as reagent]
		      [re-frame.core :as re-frame]
		      [alandipert.storage-atom :refer [local-storage load-local-storage]]
		      ))

(def iprimed-db {:name "re-frame"})