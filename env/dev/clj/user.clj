(ns user
  (:require [mount.core :as mount]
            [optiontrader.figwheel :refer [start-fw stop-fw cljs]]
            optiontrader.core))

(defn start []
  (mount/start-without #'optiontrader.core/repl-server))

(defn stop []
  (mount/stop-except #'optiontrader.core/repl-server))

(defn restart []
  (stop)
  (start))


