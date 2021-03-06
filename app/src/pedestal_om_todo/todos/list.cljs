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
            [pedestal-om-todo.models :as model]
            [pedestal-om-todo.routes :as routes]
            [pedestal-om-todo.state :as state]
            [pedestal-om-todo.utils :as util]
            [sablono.core :as html :refer [html] :include-macros true])
  (:import [pedestal-om-todo.models.Todo]))

(def ^:private app-state (atom nil))

;; ----------------------------------------------------------------------
;; Todo CRUD

(defn todos-modify
  "Re-render todos whenever they are modified"
  [_ [_ _ _ tree] _]
  (when (not (nil? @app-state))
    (let [values (vals tree)
          todos (vec (filter #(model/valid-todo? %) values))
          todos-sorted (sort-by #(:ord %) todos)]
      (om/transact! @app-state :todos (fn [_] todos-sorted)))))

(defn todos-all-completed?
  "receives updates from the app-model to determine if the
   'toggle status of all todos' checkbox should be set"
  [_ [_ _ _ value] _]
  (om/transact! @app-state :all-completed? (fn [_] value)))

(defn- new-todo-keydown [evt owner]
  (when (== (util/keyboard-event-key evt) util/ENTER_KEY)
    (let [new-field (om/get-node owner "new-field")]
      (when-not (string/blank? (.. new-field -value trim))
        (let [new-todo (model/map->Todo
                        {:id (util/uuid)
                         :title (.-value new-field)
                         :body ""
                         :ord nil
                         :completed? false
                         :created (util/now-unix-timestamp)})
              msg {msg/type :todos
                   msg/topic [:todos :modify (:id new-todo)]
                   :todo new-todo}
              input-queue @state/input-queue]
          (set! (.-value new-field) "")
          (p/put-message input-queue msg))))
    false))

(defn- todo-destroy
  "destroys a specific todo"
  [_ todo]
  (let [todo-id (:id todo)]
    (om/transact! @app-state :todos
                  (fn [todos] (into [] (remove #(= (:id %) todo-id) todos))))
    (p/put-message @state/input-queue {msg/type :todos
                                       msg/topic [:todos :modify todo-id]
                                       :todo nil})))

(defn- todo-destroy-completed
  "Sends messages to the app-model
   - toggling the [:todos :all-completed?] state
   - requesting it to destroy all completed todos.   
   
   Handled by the function pedestal-om-todo.behavior/todo-destroy-continue"
  [_]
  (let [input-queue @state/input-queue]
    (p/put-message input-queue {msg/type :todos
                                msg/topic [:todos :all-completed?]
                                :value false})
    (p/put-message input-queue {msg/type :todos
                                msg/topic [:todos :destroy]
                                :pred (fn [todo] (:completed? todo))})))

(defn- todo-status-toggle
  "toggles the status of a particular todo"
  [evt todo]
  (let [new-todo (update-in todo [:completed?] #(not %))
        input-queue @state/input-queue
        ;; get :all-completed? state.
        ;; this is a HACK for reading, and I need to think
        ;; of a better way to do this on the data-model layer
        ;; instead of the rendering layer (ie: in "behavior.clj")
        all-completed? (om/transact! @app-state :all-completed? (fn [x] x))]
    ;; Put one message to modify todo status
    (p/put-message input-queue {msg/type :todos
                                msg/topic [:todos :modify (:id todo)]
                                :todo new-todo})
    
    ;; Put another message to possibly 
    ;; toggle all-completed? status to be UNCHECKED
    (if (and all-completed? (not (:completed new-todo)))
      (p/put-message input-queue {msg/type :todos
                                  msg/topic [:todos :all-completed?]
                                  :value false}))))

(defn- todo-all-completed?-toggle
  "toggles the status of all todos"
  [_]
  (p/put-message @state/input-queue {msg/type :todos
                                     msg/topic [:todos :all-completed?]}))

(defn- todo-input-row [{:keys [all-completed? todos] :as app} owner]
  (om/component
   (html [:div#input-row
          [:span#select-all
           ;; see todo-list-item for explanation of using <span>
           ;; instead of <input> for "checkboxes"
           {:className (if (< (count todos) 1)
                         "hidden"
                         (if all-completed? "checked"))
            :onClick #(todo-all-completed?-toggle %)}]
          [:input#new-todo
           {:placeholder "What needs to be done?"
            :ref "new-field"
            :defaultValue ""
            :onKeyDown #(new-todo-keydown % owner)}]])))

(defn- list-item-visible? [todo filter]
  (case filter
    :all true
    :active (not (:completed? todo))
    :completed (:completed? todo)))

(defn- todo-list-item [todo-cursor owner]
  (let [todo (om/value todo-cursor)
        hidden-class (if (:hidden todo) "hidden" nil)]
    (reify
      om/IRender
      (render [_]
        (html [:li {:className (str "todo-item " hidden-class)}
               [:div.cont
                [:span
                 ;; We use <span> eles instead of checkbox <input> eles
                 ;; due to inconsistencies in browser behaviour when
                 ;; displaying styled checkboxes. This allows up to 
                 ;; easily draw custom ticks instead of having to deal
                 ;; with these browser inconsistencies
                 ;;
                 ;; Styling is done in CSS.
                 {:className (str "toggle-status " (if (:completed? todo) "checked"))
                  :onClick #(todo-status-toggle % todo)}]
                [:label
                 {:className (str "title " (if (:completed? todo) "title-completed"))
                  :onClick (fn [_] (routes/goto-item todo))}
                 (:title todo)]
                [:button.delete-todo.btn.btn-danger
                 {:onClick #(todo-destroy % todo)}
                 "Delete"]]])))))

(defn- todo-list-item-build
  "passed to the om/build-all function, 
  and adds additional attributes that indicate state of the todo."
  [todo filter]
  (cond-> todo
          (not (list-item-visible? todo filter)) (assoc :hidden true)))

(defn- todo-list [{:keys [filter todos] :as app} owner]
  (om/component
   (html
    [:ul#todo-list-rows
     (om/build-all todo-list-item
                   todos
                   {:key :id  ;; referring to the todo item's :id
                    :fn (fn [todo] (todo-list-item-build todo filter))})])))

;; ----------------------------------------------------------------------
;; Filters

(defn- filter-selection-class [item-filter current-filter]
  (if (= item-filter current-filter) "selected-filter"))

(defn- filter-set
  "called when a new filter is set"
  [_ [_ _ _ {:keys [filter]}] _]
  (om/transact! @app-state :filter (fn [_] (keyword filter))))

(defn- filter-select [_ filter]
  (routes/goto-list filter))

(defn- footer [{:keys [filter todos] :as app} owner]
  (let [active-count (count (remove #(:completed? %) (om/value todos)))
        completed-count (- (count todos) active-count)]
    (om/component
     (html [:div#footer.well
            [:div#remaining-count-cont
             (when (> active-count 0)
               [:div [:strong active-count] [:span " items left"]])]
            [:ul#controls
             [:li [:a#filter-all
                   {:className (filter-selection-class :all filter)
                    :onClick #(filter-select % "all")}
                   "All"]]
             [:li [:a#filter-active
                   {:className (filter-selection-class :active filter)
                    :onClick #(filter-select % "active")}
                   "Active"]]
             [:li [:a#filter-completed
                   {:className (filter-selection-class :completed filter)
                    :onClick #(filter-select % "completed")}
                   "Completed"]]]
            [:div#completed-delete-cont
             {:className (if (< completed-count 1) "hidden")}
             [:span.btn.btn-warning
              {:onClick #(todo-destroy-completed %)}
              "Delete Completed Items"]]]))))

(defn- list-app [{:keys [filter todos] :as app} owner]
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

(defn start
  "Called when the [:todo-item] node is created"
  [node-id]
  (let [app {:filter :all
             :all-completed? false
             :todos []}]
    (om/root list-app
             app
             {:target (.getElementById js/document node-id)})))
