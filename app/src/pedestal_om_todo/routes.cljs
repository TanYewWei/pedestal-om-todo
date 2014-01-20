(ns pedestal-om-todo.routes
  (:require [io.pedestal.app.protocols :as p]
            [io.pedestal.app.messages :as msg]
            [pedestal-om-todo.history :as history]
            [pedestal-om-todo.state :as state]
            [pedestal-om-todo.utils :as util]
            [secretary.core :as secretary])
  (:require-macros [secretary.macros :refer [defroute]]))

;; Think of all functions here as a way of relaying intent
;; to switch to a different focus

(def navigate ::navigate-intent)

(defn- navigate-message [path value]
  {msg/type navigate
   msg/topic (concat [:root :focus] path)
  :value value})

(defn- execute-and-return [msgs]
  (let [input-queue @state/input-queue]
    (doseq [msg msgs]
      (p/put-message input-queue msg))
    msgs))

;; ------------------------------
;; List

(defn goto-list
  ([] (goto-list :all))
  ([filter]
     (history/set-token! "/")
     (execute-and-return
      [(history/navigate :todo-list)
       ^:input {msg/type :todos msg/topic [:todos :filter] :value filter}
       ^:input {msg/type :todos
                msg/topic [:todos :modify :sentinel]
                :todo (util/guid)}])))

(defn goto-confirm-list []
  (history/set-token! "/")
  [(history/navigate :todo-list)
   ^:input {msg/type :todos
            msg/topic [:todos :modify :sentinel]
            :todo (util/guid)}])

;; ------------------------------
;; Todo Items

(defn goto-item [todo]
  (let [set-viewing ^:input {msg/type :todos
                             msg/topic [:todos :viewing]
                             :todo todo}
        set-focus {msg/type navigate
                   msg/topic [:root :focus :item]
                   :value (util/add-uuid todo)}
        set-nav (history/navigate :todo-item)
        url (str "/item/" (:id todo))
        input-queue @state/input-queue]
    (history/set-token! url)
    (p/put-message input-queue set-nav)
    (p/put-message input-queue set-viewing)
    ))

(defn goto-confirm-item [id app-model]
  (util/log "goto-confirm-item")
  (let [todo (get-in app-model [:todos :modify id])
        url  (str "/item/" id)]
    (history/set-token! url)
    [{msg/type :todos msg/topic [:todos :viewing] :todo todo}
     (history/navigate :todo-item)]))

;; ------------------------------
;; Behavior continue handler

(defn focus-handler [inputs]
  (let [message (:message inputs)]
    (when (= navigate (msg/type message))
      (let [path (msg/topic message)
            target (last path)]
        (case target
          :item (let [app-model (:new-model inputs)
                      todo-id (:id (:value message))
                      path [:todos :modify todo-id]
                      todo (get-in app-model path)]
                  (when (not (nil? todo))
                    (goto-item todo)))
          :list (goto-list))))
    []))

;; ------------------------------
;; Routes

(defroute "/" []
  (goto-list))

(defroute "/:filter" [filter]
  (goto-list filter))

(defroute "/item/:id" [id]
  (execute-and-return (navigate-message [:item] (util/add-uuid {:id id}))))

;; ------------------------------
;; Dispatcher

(defn dispatcher []
  (fn [token] (secretary/dispatch! token)))
