(ns pedestal-om-todo.routes
  (:require [io.pedestal.app.protocols :as p]
            [io.pedestal.app.messages :as msg]
            [pedestal-om-todo.history :as history]
            [pedestal-om-todo.state :as state]
            [pedestal-om-todo.utils :as util]
            [secretary.core :as secretary])
  (:require-macros [secretary.macros :refer [defroute]]))

(def navigate
  "message type for focus change intents"
  ::navigate-intent)

(defn- navigate-message
  "constructs a message that attempts to change the focus of the app.
   This is used when changing focus requires data in the app-model
   which we do not have on hand. See the /item/:id route below for
   and example use case"
  [path value]
  {msg/type navigate
   msg/topic (concat [:root :focus] path)
   :value value})

(defn- execute-and-return
  "sends a vector of messages in order to the input queue
   and returns those messages"
  [msgs]
  (let [input-queue @state/input-queue]
    (doseq [msg msgs]
      (p/put-message input-queue msg))
    msgs))

;; ------------------------------
;; List

(defn goto-list
  "switches focus to the todo list view."
  ([]
     (goto-list "all"))
  ([filter]
     ;; triggers off goto-list-worker function 
     ;; via /:filter route
     (history/set-token! (str "/" filter))))

(defn- goto-list-worker [filter]
  (execute-and-return
   [(history/navigate :todo-list)
    ^:input {msg/type :todos
             msg/topic [:todos :filter]
             :value (util/add-uuid {:filter (keyword filter)})}
    ^:input {msg/type :todos
             msg/topic [:todos :modify :req-id]
             :todo (util/uuid)}]))

;; ------------------------------
;; Todo Items

(defn goto-item
  "Switches focus to a todo item ${todo}.

   Changing focus can also be done using only a todo item's ID.
   See the /item/:id route below for an example."
  [todo]
  (let [set-viewing ^:input {msg/type :todos
                             msg/topic [:todo-item :todo]
                             :todo todo}
        set-nav (history/navigate :todo-item)
        url (str "/item/" (:id todo))
        input-queue @state/input-queue]
    (history/set-token! url)
    (execute-and-return [set-nav set-viewing])))

;; ------------------------------
;; Behavior continue handler

(defn focus-handler
  "This watches the path [:root :focus :*] for changes,
   and switches focus based on those changes.

   See the /item/:id route below for an example"
  [inputs]
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
          :list (if (nil? (:value message))
                  (goto-list)
                  (goto-list (:value message))))))))

;; ------------------------------
;; Routes

(defroute "/" []
  (goto-list))

(defroute "/:filter" [filter]
  (when (contains? #{"all" "active" "completed"} filter)
    (goto-list-worker filter)))

(defroute "/item/:id" [id]
  (execute-and-return [(navigate-message [:item] (util/add-uuid {:id id}))]))

;; ------------------------------
;; Dispatcher

(defn dispatcher
  "Called in pedestal-om-todo.start/create-app
   and used for routing browser history states to the
   correct focus based on the routes defined above"
  []
  (fn [token] (secretary/dispatch! token)))
