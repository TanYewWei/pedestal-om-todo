(ns pedestal-om-todo.start
  (:require [io.pedestal.app.protocols :as p]
            [io.pedestal.app :as app]
            [io.pedestal.app.render.push :as push-render]
            [io.pedestal.app.render :as render]
            [io.pedestal.app.messages :as msg]
            [pedestal-om-todo.behavior :as behavior]
            [pedestal-om-todo.rendering :as rendering]))

(defn create-app [render-config]
  (let [app (app/build behavior/app)
        render-fn (push-render/renderer "content" render-config render/log-fn)
        app-model (render/consume-app-model app render-fn)]
    (app/begin app)
    
    ;; Send message to create root view
    (p/put-message (:input app) {msg/type :render msg/topic [:root] :value true})

    ;; Create a todo
    (p/put-message (:input app) {msg/type :todos
                                 msg/topic [:todos :modify "123"]
                                 :todo {:id "123"
                                        :title "hello"
                                        :body "it's a good day"
                                        :ord 0
                                        :completed? false}})
    {:app app :app-model app-model}))

(defn ^:export main []
  (create-app (rendering/render-config)))
