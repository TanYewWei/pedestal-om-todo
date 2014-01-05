(ns pedestal-om-todo.html-templates
  (:use [io.pedestal.app.templates :only [tfn dtfn tnodes]]))

(defmacro pedestal-om-todo-templates
  []
  {:list-page (tfn (tnodes "list.html" "list"))
   :todo-page (tfn (tnodes "todo.html" "todo"))})
