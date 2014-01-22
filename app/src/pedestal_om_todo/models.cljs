(ns pedestal-om-todo.models
  (:require [schema.core :as s]
            [pedestal-om-todo.utils :as util])
  (:require-macros [schema.macros :as sm]))

(sm/defrecord Todo
    [id         :- s/Str
     title      :- s/Str
     body       :- (s/maybe s/Str)
     ord        :- s/Int
     completed? :- js/Boolean])

(sm/defn valid-todo? :- js/Boolean
  [x :- s/Any]
  (nil? (s/check Todo x)))
