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

(defn now-unix-timestamp
  "Returns a unix timestamp in milliseconds"
  []
  (let [d (js/Date.)
        year (.getUTCFullYear d)
        month (.getUTCMonth d)
        day (.getUTCDate d)
        hour (.getUTCHours d)
        min (.getUTCMinutes d)
        sec (.getUTCSeconds d)
        ms (.getUTCMilliseconds d)]
    (js/Date.UTC year month day hour min sec ms)))

(defn log 
  ([x] (js/console.log x))
  ([x y] (js/console.log x y)))
