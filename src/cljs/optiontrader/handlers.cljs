(ns optiontrader.handlers
    (:require [re-frame.core :as re-frame]
              [optiontrader.db :as db]
              [alandipert.storage-atom :refer [local-storage load-local-storage]]))


(re-frame/register-handler
 :initialize-db
 (fn  [_ _]
   db/iprimed-db))