;; Thanks to hhutch for this gist 
;; -- https://gist.github.com/hhutch/1131051
;; which shows an example of how to use the Google Closure
;; Rich Text Editor with clojurescript

(ns pedestal-om-todo.todos.item
  (:require [goog.dom :as dom]
            goog.editor.SeamlessField
            [goog.events :as events]
            [goog.editor.Command :as command]
            [goog.editor.plugins.BasicTextFormatter]
            [goog.editor.plugins.RemoveFormatting]
            [goog.editor.plugins.UndoRedo]
            [goog.editor.plugins.ListTabHandler]
            [goog.editor.plugins.SpacesTabHandler]
            [goog.editor.plugins.EnterHandler]
            [goog.editor.plugins.HeaderFormatter]
            [goog.editor.plugins.LinkDialogPlugin]
            [goog.editor.plugins.LinkBubble]
            [goog.editor.plugins.LoremIpsum]
            [goog.ui.editor.DefaultToolbar :as default-toolbar]
            [goog.ui.editor.ToolbarController :as toolbar-controller]
            [io.pedestal.app.messages :as msg]
            [io.pedestal.app.protocols :as p]
            [om.core :as om :include-macros true]
            [pedestal-om-todo.history :as history]
            [pedestal-om-todo.models :as model]
            [pedestal-om-todo.state :as state]
            [pedestal-om-todo.routes :as routes]
            [pedestal-om-todo.utils :as util]           
            [sablono.core :as html :refer [html] :include-macros true])
  (:import [goog.editor.Field EventType]))

(defn- save-worker
  "Updates Om state and sends a message to the app model
   to modify the current todo item."
  [todo owner input-queue]
  (om/set-state! owner :todo todo)
  (p/put-message input-queue {msg/type :todos
                              msg/topic [:todos :modify (:id todo)]
                              :todo todo}))

(defn- save
  "sends any changes the user made to todo attributes
   to the app model, updating the user interface as needed."
  [evt key current-todo owner input-queue]    
  (let [new-text (.. evt -target -value)
        todo (assoc current-todo
               key new-text
               :modified (util/now-unix-timestamp))]
    (save-worker todo owner input-queue)))

(defn- save-goog
  "save handler for the rich text editor"
  [key new-text app owner input-queue]
  (let [current-todo (:todo (om/get-state owner))
        todo (assoc current-todo
               key new-text
               :modified (util/now-unix-timestamp))]
    (save-worker todo owner input-queue)))

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
           [:input#title-input.input
            {:type "text"
             :placeholder "Add some title to the TODO ..."                  
             :value (:title todo)
             :onChange #(save % :title todo owner input-queue)}]]

          ;; Used to contain the rich text editor
          [:div#body-edit-cont
           [:div#body-edit-toolbar]
           [:div#body-edit.textarea]]]))

      ;; Setup the Google Closure Rich Text Editor
      ;; once component has mounted into the DOM
      om/IDidMount
      (did-mount [_ node]
        (let [init-body-text (:body (om/value todo))
              editor (goog.editor.SeamlessField. "body-edit" node)
              buttons (clj->js ;; don't forget that we need to pass in a JS array
                       [command/BOLD
                        command/ITALIC
                        command/UNDERLINE
                        command/FONT_COLOR
                        command/BACKGROUND_COLOR
                        command/FONT_FACE
                        command/FONT_SIZE
                        command/LINK
                        command/UNDO
                        command/REDO
                        command/UNORDERED_LIST
                        command/ORDERED_LIST
                        command/INDENT
                        command/OUTDENT
                        command/JUSTIFY_LEFT
                        command/JUSTIFY_CENTER
                        command/JUSTIFY_RIGHT
                        command/SUBSCRIPT
                        command/SUPERSCRIPT
                        command/STRIKE_THROUGH
                        command/REMOVE_FORMAT])
              save-body #(save-goog :body (.. % -target getCleanContents) app owner input-queue)
              plugins [:BasicTextFormatter
                       :RemoveFormatting
                       :UndoRedo
                       :ListTabHandler
                       :SpacesTabHandler
                       :EnterHandler
                       :LinkDialogPlugin
                       :LinkBubble]]
          
          ;; Register plugins
          (doall (map #(let [plugin (aget goog.editor.plugins (name %))]
                         (.registerPlugin editor (new plugin)))
                      plugins))

          ;; Only use LoremIpsum if there isn't any initial text to set
          ;; because this plugin will remove any initial text on focus.
          (when (empty? init-body-text)
            (.registerPlugin editor (goog.editor.plugins.LoremIpsum. "Start Typing What Needs to Get Done ...")))

          ;; Create toolbar and build a toolbar controller
          ;; to bind editor and toolbar
          (goog.ui.editor.ToolbarController. 
           editor
           (default-toolbar/makeToolbar buttons (dom/getElement "body-edit-toolbar")))          
          
          ;; Add Listeners to editor
          (events/listen editor EventType.LOAD #(.setHtml editor false init-body-text))
          (events/listen editor EventType.UNLOAD save-body)
          (events/listen editor EventType.DELAYEDCHANGE save-body)

          ;; DONE
          (.makeEditable editor))))))

(defn start [node-id todo]
  ;; called when [:todo-item :item] receives a new value
  (when (model/valid-todo? todo)
    (om/root {:todo todo}
             item-app
             (.getElementById js/document node-id))))
