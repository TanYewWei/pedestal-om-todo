(ns pedestal-om-todo.html-templates
  (:use [io.pedestal.app.templates :only [tfn dtfn tnodes]]))

(defmacro pedestal-om-todo-templates
  []
  {:root (tfn (tnodes "root.html" "root"))})
