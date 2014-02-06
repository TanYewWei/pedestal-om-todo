(ns ^:shared pedestal-om-todo.behavior
    (:require [clojure.string :as string]
              [io.pedestal.app :as app]
              [io.pedestal.app.dataflow :as dataflow]
              [io.pedestal.app.messages :as msg]
              [io.pedestal.app.protocols :as p]
              [pedestal-om-todo.models :as model]
              [pedestal-om-todo.routes :as routes]
              [pedestal-om-todo.state :as state]
              [pedestal-om-todo.utils :as util]))

;; ----------------------------------------------------------------------
;; Transforms

(defn set-value-transform [old-value message]
  (:value message))

(defn todo-modify-transform [old-value message]
  (when-let [todo (:todo message)]
    (cond
     ;; old todo has no timestamp,
     ;; just override
     (or (nil? old-value)
         (nil? (:updated old-value)))
     todo
     
     ;; both todos have timestamps,
     ;; so we should only save the latest one
     (and (:updated old-value)
          (:updated todo)
          (> (:updated todo) (:updated old-value)))
     todo         
     
     ;; else, keep our old todo
     true old-value)))

(defn refresh-transform [_ _]
  (util/uuid))

(defn todo-all-completed?-transform [old-value message]
  (let [value (:value message)]
    (cond     
     ;; use newly provided value
     (not (nil? value)) value
     
     ;; never previously set
     (nil? old-value)   true

     ;; flip switch
     true               (not old-value))))

;; ----------------------------------------------------------------------
;; Continue

(defn- todo-modify-todos [todos]
  (filter #(model/valid-todo? %) (vals todos)))

(defn todo-item-ordinal [inputs]
  (if-let [current-todo (first (vals (dataflow/added-inputs inputs)))]
    (when (and (map? current-todo) ;; todos may not have ordinal at this stage
               (not (:ord current-todo)))
      (let [todos (todo-modify-todos
                   (:new (dataflow/old-and-new inputs [:todos :modify])))
            latest-todo (apply max-key :ord  todos)
            new-ord (inc (:ord latest-todo))
            new-todo (assoc current-todo :ord new-ord)]
        [{msg/type :todos
          msg/topic [:todos :modify (:id new-todo)]
          :todo new-todo}]))))

(defn todo-destroy
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

(defn todo-all-completed
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

;; ----------------------------------------------------------------------
;; Dataflow

(defn init-todos [x]
  [{:todo-list {}}
   {:todo-item {}}
   {:root {:focus {}}}])

(def app
  {:version 2
   :debug true
   :transform [[routes/navigate [:root :focus :*] set-value-transform]
               [:todos [:todos :filter] set-value-transform]
               [:todos [:todos :modify] set-value-transform]
               [:todos [:todos :modify :*] todo-modify-transform]
               [:todos [:todos :all-completed?] todo-all-completed?-transform]
               [:todos [:todos :destroy] refresh-transform]
               [:todos [:todo-item :todo] todo-modify-transform]]
   :continue #{[#{[:todos :all-completed?]} todo-all-completed]
               [#{[:todos :modify :*]} todo-item-ordinal]
               [#{[:todos :destroy]} todo-destroy]
               [#{[:root :focus]} routes/focus-handler]}
   :emit [{:init init-todos}
          [#{[:todo-list :*]
             [:todo-item :*]
             [:todos :filter]
             [:todos :all-completed?]
             [:todos :modify]} (app/default-emitter [])]]
   :focus {:todo-list [[:todo-list] [:todos]]
           :todo-item [[:todo-item] [:todos]]
           :root [[:root]]
           :default :root}})
