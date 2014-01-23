(ns pedestal-om-todo.todos.item
  (:require [cljs.core.async :refer [<! put! chan]]
            [io.pedestal.app.messages :as msg]
            [io.pedestal.app.protocols :as p]
            [io.pedestal.app.render.events :as events]
            [om.core :as om :include-macros true]
            [pedestal-om-todo.history :as history]
            [pedestal-om-todo.models :as model]
            [pedestal-om-todo.state :as state]
            [pedestal-om-todo.routes :as routes]
            [pedestal-om-todo.utils :as util]           
            [sablono.core :as html :refer [html] :include-macros true])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def ^:private current-todo (atom {}))
(def ^:private app-state (atom nil))

(defn view-item
  "we receive the [:node-create [:todo-item] :map] delta first,
   then the [:value [:todos :viewing] _ _] delta.
   The latter is processed by this function"
  [_ [_ _ _ todo] _]
  (when (model/valid-todo? todo)
    (util/log "view-item" todo)
    (when-not (nil? @app-state)
      (om/transact! @app-state :todo (fn [_] todo))
      (om/transact! @app-state :title (fn [_] (:title todo)))
      (om/transact! @app-state :body (fn [_] (:body todo)))
      (util/log "set app-state:" (str (om/read @app-state #(om/value %)))))
    (reset! current-todo todo)
    ))

(defn- input-change! [owner todo]
  ;;(om/transact! @app-state :todo #(assoc % key text))
  (om/set-state! owner :todo todo)
  )

(defn save
  "sends any changes the user made to todo attributes
   to the app model, updating the user interface as needed."
  [evt owner current-todo key comm input-queue]
  ;;(util/log "current-todo:" (om/read current-todo (fn [x] (om/value x))))
    
  (let [new-text (.. evt -target -value)
        todo (assoc (om/read current-todo (fn [x] (om/value x)))
               key new-text
               :timestamp (util/now-unix-timestamp))
        todo-id (:id todo)
        msg-modify {msg/type :todos
                    msg/topic [:todos :modify todo-id]
                    :todo todo}
        msg-viewing {msg/type :todos
                     msg/topic [:todos :viewing]
                     :todo todo}
        ]
    (om/set-state! owner key new-text)
    
    ;; update input value
    ;;(input-change! owner todo)

    ;; Send message to app-model via core.async channel.
    ;; This enables us to send every significant update to the
    ;; without having to 
    (p/put-message input-queue msg-modify)
    ;;(p/put-message input-queue msg-viewing)
    ;; (put! comm [:save {:msgs [msg-modify msg-viewing]
    ;;                    :input-queue input-queue}])
    ))

(defn- handle-save [{:keys [msgs input-queue]}]
  (events/send-transforms input-queue msgs))

(defn- handle-event [type value app]
  (case type
    :save (handle-save value)))

(defn item-app [{:keys [todo] :as app} owner]
  (let [input-queue @state/input-queue
        ;;todo (:todo app)
        ;;todo-val (om/value todo)
        ]
    (reify
      ;; om/IInitState
      ;; (init-state [_]
      ;;   (util/log "init-state:" (str (om/value todo)))
      ;;   (select-keys (om/value todo) [:title :body]))

      om/IWillMount
      (will-mount [_]
        ;; Create a core.async channel used for saving todo items.
        ;; See the save function above for details on the save loop
        (let [comm (chan)]
          (om/set-state! owner :comm comm)
          ;; (p/put-message input-queue {msg/type :todos 
          ;;                             msg/topic [:todos :view-intent]
          ;;                             :id (:id todo)})
          (go (while true
                (let [[type value] (<! comm)]
                  (handle-event type value app)))))
        
        ;; Create a reference to app via app-state
        (reset! app-state app))

      om/IRenderState
      (render-state [_ {:keys [comm title]}]
        (util/log "render title:" title)
        (html [:div#item-cont.well
               [:div
                [:button#back-button.btn.btn-info
                 {:ref "back-button"
                 :onClick (fn [_] (history/back!))}
                 "Back"]]
               [:h4 "Edits are automatically saved"]
               [:div
                [:input#title-input
                 {:ref "title-edit"
                  :type "text"
                  :placeholder "Add some title to the TODO ..."                  
                  :value title
                  :onChange #(save % owner todo :title comm input-queue)}]]
               [:div
                [:textarea#body-edit
                 {:ref "body-edit"
                  :placeholder "Start Typing What Needs to Get Done ..."
                  :onChange #(save % owner todo :body comm input-queue)
                  :value (:body todo)}]]]))

      om/IWillUnmount
      (will-unmount [_]
        ;; we're no longer viewing a particular todo
        (p/put-message input-queue {msg/type :todos 
                                    msg/topic [:todos :viewing]
                                    :todo nil})
        (reset! app-state nil)))))

(defn start [node-id]
  (om/root {:todo {}}
           item-app
           (.getElementById js/document node-id)))
