(ns pedestal-om-todo.todos.item
  (:require [io.pedestal.app.messages :as msg]
            [io.pedestal.app.protocols :as p]
            [pedestal-om-todo.utils :as util]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer [html] :include-macros true]))

(def ^:private current-item (atom nil))
(def ^:private app-state (atom nil))

(defn view-item [renderer [_ _ _ todo] _]
  (reset! current-item todo))

(defn goto-list [evt input-queue]
  (p/put-message input-queue {msg/type :todos
                              msg/topic [:todos :viewing]
                              :todo nil}))

(defn save [evt owner input-queue]
  (let [title (.-value (om/get-node owner "title-edit"))
        body (.-value (om/get-node owner "body-edit"))
        todo (assoc @current-item
               :title title
               :body body)
        todo-id (:id todo)
        msg {msg/type :todos
             msg/topic [:todos :modify todo-id]
             :todo todo}]
    (p/put-message input-queue msg)))

(defn item-app [{:keys [todo] :as app} owner]
  ;; todo is an Om cursor (representing a virtual DOM ele)
  (let [input-queue (:input-queue (om/value app))]
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
                 :onClick #(goto-list % input-queue)}
                 "Back"]]
               [:h4 "Edits are automatically saved"]
               [:div
                [:input#title-input
                 {:ref "title-edit"
                  :type "text"
                  :placeholder "Add some title to the TODO ..."
                  :defaultValue (:title todo)
                  :onKeyUp #(save % owner input-queue)}]]
               [:div
                [:textarea#body-edit
                 {:ref "body-edit"
                  :placeholder "Start Typing What Needs to Get Done ..."
                  :onKeyUp #(save % owner input-queue)
                  :defaultValue (:body todo)}]]]))

      om/IWillUnmount
      (will-unmount [_]
        (util/log "item-app unmount")
        (reset! app-state nil)))))

(defn start [input-queue node-id]
  (om/root {:input-queue input-queue :todo @current-item}
           item-app
           (.getElementById js/document node-id)))


(defn build-app [{:keys [todos] :as app} owner {:keys [input-queue] :as opts}]
  (reify
    om/IWillMount
    (will-mount [_]
      (util/log "item will-mount"))

    om/IRender
    (render [_]
      (html [:div#item-cont.well
             [:div
              [:button#back-button.btn.btn-info
               {:ref "back-button"
                :onClick #(goto-list % input-queue)}
               "Back"]]
             [:h4 "Edits are automatically saved"]
             [:div
              [:input#title-input
               {:ref "title-edit"
                :type "text"
                :placeholder "Add some title to the TODO ..."
                :defaultValue (:title todo)
                :onKeyUp #(save % owner input-queue)}]]
             [:div
              [:textarea#body-edit
               {:ref "body-edit"
                :placeholder "Start Typing What Needs to Get Done ..."
                :onKeyUp #(save % owner input-queue)
                :defaultValue (:body todo)}]]]))

    om/IWillUnmount
    (will-unmount [_]
      (util/log "item will-unmount"))))
