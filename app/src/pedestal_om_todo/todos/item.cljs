(ns pedestal-om-todo.todos.item
  (:require [io.pedestal.app.messages :as msg]
            [io.pedestal.app.protocols :as p]
            [pedestal-om-todo.history :as history]
            [pedestal-om-todo.state :as state]
            [pedestal-om-todo.routes :as routes]
            [pedestal-om-todo.utils :as util]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer [html] :include-macros true]))

(def ^:private app-state (atom nil))

(defn view-item [_ [_ _ _ todo] _]
  (when (map? todo)
    (om/transact! @app-state :todo (fn [_] todo))))

(defn- input-change! [owner key text]
  (om/transact! @app-state :todo #(assoc % key text)))

(defn save
  "sends any changes the user made to todo attributes
   to the app model, updating the user interface as needed."
  [evt owner current-todo key input-queue]
  (let [new-text (.. evt -target -value)
        todo (assoc current-todo key new-text)
        todo-id (:id todo)
        msg {msg/type :todos
             msg/topic [:todos :modify todo-id]
             :todo todo}]
    ;; update input value
    (input-change! owner key new-text)

    ;; send message to app-model
    (p/put-message input-queue msg)))

(defn item-app [app owner]
  (let [input-queue @state/input-queue
        todo (:todo (om/value app))]
    (reify
      om/IWillMount
      (will-mount [_]
        (reset! app-state app))

      om/IRender
      (render [_]
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
                  :value (:title todo)
                  :onChange #(save % owner todo :title input-queue)}]]
               [:div
                [:textarea#body-edit
                 {:ref "body-edit"
                  :placeholder "Start Typing What Needs to Get Done ..."
                  :onChange #(save % owner todo :body input-queue)
                  :value (:body todo)}]]]))

      om/IWillUnmount
      (will-unmount [_]
        ;; we're no longer viewing a particular todo
        (p/put-message input-queue {msg/type :todos 
                                    msg/topic [:todos :viewing]
                                    :todo nil})
        (reset! app-state nil)))))

(defn start [node-id]
  (om/root {:todo {}} item-app (.getElementById js/document node-id)))
