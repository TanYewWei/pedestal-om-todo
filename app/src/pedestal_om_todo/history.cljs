(ns pedestal-om-todo.history
  (:require [goog.events :as events]
            [io.pedestal.app.protocols :as p]
            [io.pedestal.app.messages :as msg]
            [pedestal-om-todo.utils :as util])
  (:import [goog History]
           [goog.History EventType]))

(def last-focus (atom nil))

(def history (History.))

(defn start [dispatcher]
  (events/listen history EventType/NAVIGATE #(dispatcher (.-token %)))
  (.setEnabled history true))

(defn navigate [focus]
  ^:input {msg/type :navigate msg/topic msg/app-model :name focus})

(defn set-token! [token]
  (.setToken history token))

(defn navigate!
  "Adds a new item to History with a specified ${token}.

   Returns a message to navigate to a foci with name ${focus}.
   This message is intended to be sent to the input queue,"
  [focus token]
  (set-token! token)
  (navigate focus))
