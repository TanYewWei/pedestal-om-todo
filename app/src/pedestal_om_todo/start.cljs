(ns pedestal-om-todo.start
  (:require [io.pedestal.app.protocols :as p]
            [io.pedestal.app :as app]
            [io.pedestal.app.render.push :as push-render]
            [io.pedestal.app.render :as render]
            [io.pedestal.app.messages :as msg]
            [pedestal-om-todo.behavior :as behavior]
            [pedestal-om-todo.history :as history]
            [pedestal-om-todo.routes :as routes]
            [pedestal-om-todo.state :as state]
            [pedestal-om-todo.rendering :as rendering]))

(defn create-app [render-config]
  (let [app (app/build behavior/app)
        input-queue (:input app)
        render-fn (push-render/renderer "content" render-config render/log-fn)
        app-model (render/consume-app-model app render-fn)]
    (app/begin app)

    ;; Set State
    (reset! state/input-queue input-queue)
    
    ;; mock todo
    (p/put-message input-queue {msg/type :todos
                                msg/topic [:todos :modify "123"]
                                :todo {:id "123"
                                       :title "hello"
                                       :body "it's a good day"
                                       :ord 0
                                       :completed? false}})

    ;; Set routes dispatcher
    (history/start (routes/dispatcher))

    ;; Done
    {:app app :app-model app-model}))

(defn ^:export main []
  (create-app (rendering/render-config)))
