(ns meuse.api.default
  (:require [meuse.metric :as metric]
            [clojure.tools.logging :refer [info warn error]]))

(defn not-found
  [request]
  (info "public uri not found:" (:request-method request) (:uri request))
  (metric/http-errors request 404)
  {:status 404
   :body {:errors
          [{:detail "not found"}]}})
