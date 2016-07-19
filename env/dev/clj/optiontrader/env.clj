(ns optiontrader.env
  (:require [selmer.parser :as parser]
            [clojure.tools.logging :as log]
            [optiontrader.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[optiontrader started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[optiontrader has shut down successfully]=-"))
   :middleware wrap-dev})
