(ns pedestal-om-todo.todos.list
  (:require [clojure.string :as string]
            [clojure.set :as set]
            [domina :as domina]
            [io.pedestal.app.messages :as msg]
            [io.pedestal.app.protocols :as p]
            [io.pedestal.app.render.push :as render]
            [io.pedestal.app.render.push.handlers :as h]
            [io.pedestal.app.render.push.handlers.automatic :as d]
            [io.pedestal.app.render.push.templates :as templates]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [pedestal-om-todo.utils :as util]
            [sablono.core :as html :refer [html] :include-macros true]))

(def ^:private app-state
  ""
  (atom nil))

(defn- goto-item [evt todo input-queue]
  (let [msg {msg/type :todos
             msg/topic [:todos :viewing]
             :todo todo}
        root-node-id (om/read @app-state :root-node-id (fn [x] x))]
    (p/put-message input-queue msg)))

(defn- new-todo-keydown [evt owner input-queue]
  (when (== (util/keyboard-event-key evt) util/ENTER_KEY)
    (let [new-field (om/get-node owner "new-field")]
      (when-not (string/blank? (.. new-field -value trim))
        (let [new-todo {:id (util/guid)
                        :title (.-value new-field)
                        :body ""
                        :ord nil
                        :completed? false}
              msg {msg/type :todos
                   msg/topic [:todos :modify (:id new-todo)]
                   :todo new-todo}]
          (set! (.-value new-field) "")
          (p/put-message input-queue msg))))
    false))

;; ----------------------------------------------------------------------
;; Todo CRUD

(defn todos-modify [_ [_ _ _ tree] _]
  (when (not (nil? @app-state))
    (let [values (vals tree)
          todos (vec (filter #(map? %) values))]
      (om/transact! @app-state :todos (fn [_] todos)))))

(defn todos-all-completed? [_ [_ _ _ value] _]
  (om/transact! @app-state :all-completed? (fn [_] value)))

(defn- todo-destroy [evt todo input-queue]
  (let [todo-id (:id todo)]
    (om/transact! @app-state :todos
                  (fn [todos] (into [] (remove #(= (:id %) todo-id) todos))))
    (p/put-message input-queue {msg/type :todos
                                msg/topic [:todos :modify todo-id]
                                :todo nil})))

(defn- todo-destroy-completed [evt input-queue]
  (p/put-message input-queue {msg/type :todos
                              msg/topic [:todos :all-completed?]
                              :value false})
  (p/put-message input-queue {msg/type :todos
                              msg/topic [:todos :destroy]
                              :pred (fn [todo] (:completed? todo))}))

(defn- todo-status-toggle [evt todo input-queue]
  (let [new-todo (update-in todo [:completed?] #(not %))]
    ;; Put one message to modify todo status
    (p/put-message input-queue {msg/type :todos
                                msg/topic [:todos :modify (:id todo)]
                                :todo new-todo})
    
    ;; Put another message to possibly 
    ;; toggle all-completed? status to be UNCHECKED
    (if (and (:completed? todo)
             (om/read @app-state :all-completed? (fn [x] x)))
      (p/put-message input-queue {msg/type :todos
                                  msg/topic [:todos :all-completed?]
                                  :value false}))))

(defn- todo-all-completed?-toggle [evt input-queue]
  (p/put-message input-queue {msg/type :todos
                              msg/topic [:todos :all-completed?]}))

(defn- todo-input-row [{:keys [all-completed? todos queue] :as app} owner]
  (let [input-queue (om/value queue)]
    (om/component
     (html [:div#input-row
            [:span#select-all
             {:className (if (< (count todos) 1)
                           "hidden"
                           (if all-completed? "checked"))
              :onClick #(todo-all-completed?-toggle % input-queue)}]
            [:input#new-todo
             {:placeholder "What needs to be done?"
              :ref "new-field"
              :onKeyDown #(new-todo-keydown % owner input-queue)}]]))))

(defn- list-item-visible? [todo filter]
  (case filter
    :all true
    :active (not (:completed? todo))
    :completed (:completed? todo)))

(defn- todo-list-item [todo-cursor owner {:keys [hidden input-queue] :as opts}]
  (let [todo (om/value todo-cursor)
        hidden-class (if (:hidden todo) "hidden" nil)]
    (om/component
     (html [:li {:className (str "todo-item " hidden-class)}
            [:div.cont
             [:span
              {:className (str "toggle-status " (if (:completed? todo) "checked"))
               :onClick #(todo-status-toggle % todo input-queue)}]
             [:label
              {:className (str "title " (if (:completed? todo) "title-completed"))
               :onClick #(goto-item % todo input-queue)}
              (:title todo)]
             [:button.delete-todo.btn.btn-danger
              {:onClick #(todo-destroy % todo input-queue)}
              "Delete"]]]))))

(defn- todo-list-item-build
  "passed to the om/build-all function, 
  and adds additional attributes that indicate state of the todo."
  [todo filter]
  (cond-> todo
          (not (list-item-visible? todo filter)) (assoc :hidden true)))

(defn- todo-list [{:keys [filter todos queue] :as app} owner]
  (let [input-queue (om/value queue)]
    (om/component
     (html
      [:ul#todo-list-rows
       (om/build-all todo-list-item
                     todos
                     {:key :id  ;; referring to the todo item's :id
                      :opts {:input-queue input-queue}
                      :fn (fn [todo] (todo-list-item-build todo filter))})]))))

;; ----------------------------------------------------------------------
;; Filters

(defn- filter-selection-class [item-filter current-filter]
  (if (= item-filter current-filter) "selected-filter"))

(defn- filter-set [_ [_ _ _ filter] _]
  (om/transact! @app-state :filter (fn [_] (keyword filter))))

(defn- filter-select [evt filter input-queue]
  (p/put-message input-queue {msg/type :todos
                              msg/topic [:todos :filter]
                              :value filter}))

(defn- footer [{:keys [filter todos queue] :as app} owner]
  (let [input-queue (om/value queue)        
        active-count (count (remove #(:completed? %) (om/value todos)))
        completed-count (- (count todos) active-count)]
    (om/component
     (html [:div#footer.well
            [:div#remaining-count-cont
             (when (> active-count 0)
               [:div [:strong active-count] [:span " items left"]])]
            [:ul#controls
             [:li [:a#filter-all
                   {:className (filter-selection-class :all filter)
                    :onClick #(filter-select % :all input-queue)}
                   "All"]]
             [:li [:a#filter-active
                   {:className (filter-selection-class :active filter)
                    :onClick #(filter-select % :active input-queue)}
                   "Active"]]
             [:li [:a#filter-completed
                   {:className (filter-selection-class :completed filter)
                    :onClick #(filter-select % :completed input-queue)}
                   "Completed"]]]
            [:div#completed-delete-cont
             {:className (if (< completed-count 1) "hidden")}
             [:span.btn.btn-warning
              {:onClick #(todo-destroy-completed % input-queue)}
              "Delete Completed Items"]]]))))

(defn- list-app [{:keys [filter todos queue] :as app} owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (reset! app-state app))

    om/IRender
    (render [_]
      (html [:div
             [:h1 "Todos"]
             (om/build todo-input-row app)
             (om/build todo-list app)               
             (om/build footer app)]))

    om/IWillUnmount
    (will-unmount [_]
      (reset! app-state nil))))

(defn start [input-queue node-id]
  (let [app {:filter :all
             :all-completed? false
             :queue input-queue
             :todos []}]
    (om/root app list-app (.getElementById js/document node-id))))
