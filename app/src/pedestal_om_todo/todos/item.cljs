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

(defn save
  "sends any changes the user made to todo attributes
   to the app model, updating the user interface as needed."
  [evt owner current-todo key input-queue]    
  (let [new-text (.. evt -target -value)
        todo (assoc current-todo
               key new-text
               :modified (util/now-unix-timestamp))
        todo-id (:id todo)
        msg {msg/type :todos
             msg/topic [:todos :modify todo-id]
             :todo todo}]
    ;; Update inputs and send message to app-model
    (om/set-state! owner :todo todo)
    (p/put-message input-queue msg)))

(defn item-app [{:keys [todo] :as app} owner]
  (let [input-queue @state/input-queue]
    (reify
      om/IInitState
      (init-state [_]
        {:todo (om/value todo)})
      
      om/IRenderState
      (render-state [_ {:keys [todo]}]
        (html
         [:div#item-cont.well
          [:div
           [:button#back-button.btn.btn-info
            {:onClick (fn [_] (history/back!))}
            "Back"]]
          [:h4 "Edits are automatically saved"]
          [:div
           [:input#title-input
            {:type "text"
             :placeholder "Add some title to the TODO ..."                  
             :defaultValue (:title todo)
             :onChange #(save % owner todo :title input-queue)}]]
          [:div
           [:textarea#body-edit
            {:placeholder "Start Typing What Needs to Get Done ..."
             :onChange #(save % owner todo :body input-queue)
             :defaultValue (:body todo)}]]])))))

(defn start [node-id todo]
  ;; called when [:todo-item :]
  (when (model/valid-todo? todo)
    (om/root {:todo todo}
             item-app
             (.getElementById js/document node-id))))
