(ns ^:shared pedestal-om-todo.behavior
    (:require [clojure.string :as string]
              [io.pedestal.app :as app]
              [io.pedestal.app.dataflow :as dataflow]
              [io.pedestal.app.messages :as msg]
              [pedestal-om-todo.utils :as util])
    (:import [goog.ui IdGenerator]))

;; ----------------------------------------------------------------------
;; App Creation

(defn start-transform [_ _] true)

(defn start-app-continue [inputs]
  (if (:new (dataflow/old-and-new inputs [:root]))
    [^:input {msg/type :set-focus msg/topic msg/app-model :name :todo-list}]))

;; ----------------------------------------------------------------------
;; Transforms

(defn swap-transform [_ message]
  (:value message))

(defn set-value-transform [old-value message]
  (:value message))

(defn todo-modify-transform [old-value message]
  (:todo message))

(defn refresh-transform [_ _]
  (util/uuid))

(defn todo-all-completed?-transform [old-value message]
  (let [value (:value message)]
    (cond 
     (not (nil? value)) value
     (nil? old-value)   true
     true               (not old-value))))

;; ----------------------------------------------------------------------
;; Continue

(defn- todo-modify-todos [todos]
  (filter #(map? %) (vals todos)))

(defn todo-item-view-continue [inputs]
  (let [viewing (dataflow/old-and-new inputs [:todos :viewing])
        old-val (:old viewing)
        new-val (:new viewing)]
    (when (or old-val new-val)
      (if (and (nil? old-val) new-val)
        ;; navigating from list to item view
        ;; (sending todo to [:todos :viewing :todo] path)
        [{msg/type :todos msg/topic [:todos :viewing] :todo new-val}
         ^:input {msg/type :set-focus msg/topic msg/app-model :name :todo-item}]
        
        ;; navigating from item view back to list
        ;; (need to set sentinel to refresh tree)
        [^:input {msg/type :set-focus msg/topic msg/app-model
                  :name :todo-list}
         ^:input {msg/type :todos 
                  msg/topic [:todos :modify :sentinel]
                  :todo (util/guid)}]))))

(defn todo-item-ordinal-continue [inputs]  
  (if-let [current-todo (first (vals (dataflow/added-inputs inputs)))]
    (when (and (map? current-todo)
               (not (:ord current-todo)))
      (let [todos (todo-modify-todos
                   (:old (dataflow/old-and-new inputs [:todos :modify])))
            latest-todo (apply max-key :ord  todos)
            new-ord (inc (:ord latest-todo))
            new-todo (assoc current-todo :ord new-ord)]
        [{msg/type :todos
          msg/topic [:todos :modify (:id new-todo)]
          :todo new-todo}]))))

(defn todo-destroy-continue
  "the input message should contain a key :pred which is 
   a predicate function that should take a todo item map,
   and return true if the todo item should be deleted"
  [inputs]
  (let [destroy (dataflow/old-and-new inputs [:todos :destroy])]
    (when (not (= (:old destroy) (:new destroy)))
      (let [message (:message inputs)
            pred (:pred message)
            todos-map (:new (dataflow/old-and-new inputs [:todos :modify]))
            todos (todo-modify-todos todos-map)
            destroy-ids (map (fn [todo] (if (pred todo) (:id todo))) todos)
            new-todos (apply dissoc todos-map destroy-ids)]
        (if (map? new-todos)
          [{msg/type :todos msg/topic [:todos :modify] :value new-todos}
           ^:input {msg/type :todos msg/topic [:todos :all-completed?] :value false}])))))

(defn todo-all-completed?-continue
  [inputs]
  (let [message (:message inputs)
        completed? (dataflow/old-and-new inputs [:todos :all-completed?])]
    (when (and (nil? (:value message)) ;; only force toggle when no explicit value is sent
               (not (= (:old completed?) (:new completed?))))
      (let [todos (todo-modify-todos
                   (:old (dataflow/old-and-new inputs [:todos :modify])))
            update-fn (fn [todo]
                        (let [id (:id todo)
                              new-todo (assoc todo :completed? (:new completed?))]
                          {id new-todo}))
            new-todos (into {} (map update-fn todos))]
        [{msg/type :todos msg/topic [:todos :modify] :value new-todos}]))))

(defn init-todos [x]
  [{:todo-list {}}
   {:todo-item {}}])

(def app
  ;; There are currently 2 versions (formats) for dataflow
  ;; description: the original version (version 1) and the current
  ;; version (version 2). If the version is not specified, the
  ;; description will be assumed to be version 1 and an attempt
  ;; will be made to convert it to version 2.
  {:version 2
   :debug true
   :transform [[:render [:root] start-transform]
               [:todos [:todos :filter] set-value-transform]
               [:todos [:todos :modify] set-value-transform]
               [:todos [:todos :modify :*] todo-modify-transform]
               [:todos [:todos :all-completed?] todo-all-completed?-transform]
               [:todos [:todos :destroy] refresh-transform]
               [:todos [:todos :viewing] todo-modify-transform]]
   :continue #{[#{[:todos :viewing]} todo-item-view-continue]
               [#{[:todos :all-completed?]} todo-all-completed?-continue]
               [#{[:todos :modify :*]} todo-item-ordinal-continue]
               [#{[:todos :destroy]} todo-destroy-continue]
               [#{[:root]} start-app-continue]}
   :emit [{:init init-todos}
          [#{[:root]
             [:todo-list :*]
             [:todo-item :*]
             [:todos :filter]
             [:todos :all-completed?]
             [:todos :modify]
             [:todos :viewing]} (app/default-emitter [])]]
   :focus {:todo-list [[:todo-list] [:todos]]
           :todo-item [[:todo-item] [:todos]]
           :root [[:root]]
           :default :root}
   })
