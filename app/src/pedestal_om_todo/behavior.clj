(ns ^:shared pedestal-om-todo.behavior
    (:require [clojure.string :as string]
              [io.pedestal.app.messages :as msg]))
;; While creating new behavior, write tests to confirm that it is
;; correct. For examples of various kinds of tests, see
;; test/pedestal_om_todo/behavior-test.clj.

(defn set-value-transform [old-value message]
  (:value message))

(defn init-list [_]
  )

(defn init-todo [_]
  [{:}])

(def app
  ;; There are currently 2 versions (formats) for dataflow
  ;; description: the original version (version 1) and the current
  ;; version (version 2). If the version is not specified, the
  ;; description will be assumed to be version 1 and an attempt
  ;; will be made to convert it to version 2.
  {:version 2
   :debug true
   :transform [[:set-value [:greeting] set-value-transform]]
   :emit [{:init init-list}
          {:init init-todo}]
   :focus {:list [[:list]]
           :todo [[:todo]]
           :default :list}})
