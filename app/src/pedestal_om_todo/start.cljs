(ns pedestal-om-todo.start
  (:require [io.pedestal.app.protocols :as p]
            [io.pedestal.app :as app]
            [io.pedestal.app.render.push :as push-render]
            [io.pedestal.app.render :as render]
            [io.pedestal.app.messages :as msg]
            [pedestal-om-todo.behavior :as behavior]
            [pedestal-om-todo.rendering :as rendering]))

;; In this namespace, the application is built and started.

(defn create-app [render-config]
  (let [app (app/build behavior/app)
        render-fn (push-render/renderer "content" render-config render/log-fn)
        app-model (render/consume-app-model app render-fn)]
    (app/begin app)
    {:app app :app-model app-model}))

(defn ^:export main []
  (create-app (rendering/render-config)))

;; (defn ^:export main []
;;   (let [app (create-app (rendering/render-config))
;;         services (services/->Services (:app app))]
;;     (app/consume-effects (:app app) services/services-fn)
;;     (p/start services)
;;     app))
