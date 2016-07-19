(ns optiontrader.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [optiontrader.core-test]))

(doo-tests 'optiontrader.core-test)

