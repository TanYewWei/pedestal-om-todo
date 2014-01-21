(ns pedestal-om-todo.utils
  (:require [cljs-uuid.core :as uuid]))

(def app-ref (atom nil))

(def ENTER_KEY 13)

(defn uuid [] (.-uuid (uuid/make-random)))

(defn keyboard-event-key
  "See http://facebook.github.io/react/docs/events.html#keyboard-events"
  [evt]
  (.-which evt))

(defn add-uuid
  "adds a uuid to a map. Intended to help with refresh behaviour"
  [m]
  (assoc m ::ts (uuid)))

(defn log 
  ([x] (js/console.log x))
  ([x y] (js/console.log x y)))
