(ns pedestal-om-todo.history
  (:require [goog.events :as events]
            [io.pedestal.app.protocols :as p]
            [io.pedestal.app.messages :as msg]
            [pedestal-om-todo.utils :as util])
  (:import [goog History]
           [goog.History EventType]))

(def ^:private history (History.))

(defn start [dispatcher]
  (events/listen history EventType/NAVIGATE #(dispatcher (.-token %)))
  (.setEnabled history true))

(defn navigate [focus]
  ^:input {msg/type :navigate msg/topic msg/app-model :name focus})

(defn set-token! [token]
  (.setToken history token))

(defn back! []
  (.back window/history))
