(ns pedestal-om-todo.rendering
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
            [pedestal-om-todo.todos.list :as todo-list]
            [pedestal-om-todo.todos.item :as todo-item])
  (:require-macros [pedestal-om-todo.html-templates :as html-templates]))

(def templates (html-templates/pedestal-om-todo-templates))

(def ^:private root-id
  "Instead of swapping out HTML templates,
   we instead only create and destroy Om roots."
  (util/uuid))

(defn create-root [renderer [_ path :as delta] input-queue]
  (let [parent (render/get-parent-id renderer path)
        html (:root templates)]
    (domina/append! (domina/by-id parent) (html {:id root-id}))))

(defn add-list-template [_ _ _]
  (todo-list/start root-id))

(defn add-todo-template [_ _ _]
  (todo-item/start root-id))

(defn destroy-view
  "unmount the root React component, and leave the renderer as is"
  [r [_ path] _]
  (React/unmountComponentAtNode (.getElementById js/document root-id))
  (if-let [id (render/get-id r path)]
    (render/delete-id! r path)))

;; ----------------------------------------------------------------------
;; Config

(defn render-config []
  [;; Root View never gets destroyed
   [:node-create [:root] create-root]

   ;; list
   [:node-create [:todo-list] add-list-template]
   [:node-destroy [:todo-list] destroy-view]

   ;; item view
   [:node-create [:todo-item] add-todo-template]
   [:node-destroy [:todo-item] destroy-view]

   ;; todos
   [:node-destroy [:todos :modify] h/default-destroy]
   [:value [:todos :modify] todo-list/todos-modify]
   [:value [:todos :all-completed?] todo-list/todos-all-completed?]
   [:value [:todos :viewing] todo-item/view-item]
   [:value [:todos :filter] todo-list/filter-set]
   ])
