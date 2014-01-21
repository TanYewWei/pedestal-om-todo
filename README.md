# pedestal-om-todo

This is just a toy experiment as an attempt to use [David Nolen's Om](https://github.com/swannodette/om) along side [Cognitect's Pedestal](https://github.com/pedestal/pedestal) to implement a [TodoMVC example](http://todomvc.com).

## Spec

Each todo will have the following attributes:

* **id** (string) - a globally-unique ID for the todo
* **title** (string) - the title of the todo
* **body** (string) - details of the todo
* **ord** (integer) - the ordinal of a todo with respect to all other todos
* **completed?** (boolean) - true if the todo has been completed

A user of the app should be able to:

* **Add a todo item**

* **Delete a todo item** - click the red 'Delete' button next to the item

* **Delete all completed todo items** - this button should only appear at the bottom left when there are. Clicking

* **Filter todo items by their status** - only show me one of "all todos", "completed todos", "active todos"

* **Check a todo item as being completed or active** - click the tick next to the item to toggle the status of a todo

* **Check all todo items as being completed or active** - only visible when there are todo items. Clicking it should toggle all todos to be active or completed. If this is set to completed, and a particular todo is made active again, this should also become active.

* **View and edit details of a todo item** - clicking a todo should bring you to a detailed item view where you can edit the title and body.

The following routes (URLs) are available:

* `/` - shows all todos as a list
* `/:filter` - shows all todos as list that have the status `:filter`. `:filter` is one of `all`, `active`, or `completed`
* `/item/:id` - shows the detailed view of the todo item with id being `:id`

## Weird Stuff

Modifications had to be made in order to use Om in the application. Below are some gotchas that I ran into while building what is currently on master. Many of these are opposed to Pedestal's spirit of having pure transformation on data, but oh well .....

### Focus

There are 2 foci in the app -- a List view, and an Item view.

The List view shows a list of todos, filtered to show only those that satisfy the supplied filter. See the `pedestial-om-todo.todos.list` namespace for implementation.

The Item view shows the details of a todo item, and allows the user to edit the title and body. See the `pedestial-om-todo.todos.item` namespace for implementation.

Each of those views is an Om app. As such we don't swap templates in and out like shown in the tutorial.

Instead, what happens is that a root HTML template is initiated on startup, and then the appropriate Om apps are mounted and unmounted as focus changes. See the `pedestal-om-todo.rendering` namespace for the intended render config used to implement this behaviour.

A side effect of this is that the `design` aspect of standard Pedestal is basically useless, since everything is populated "dynamically" by javascript. The upside of using Om. Personally, I find that recording a series of deltas and then replaying them to be an adequate means of testing. This probably doesn't scale that well with larger apps, so I'll have to look into better methods of unit testing.

### Routing

Routing is implementated in the `pedestal-om-todo.history` and `pedestal-om-todo.routes` namespaces.

Start by looking at `pedestal-om-todo.routes`, whereby the routes are defined using [secretary](https://github.com/gf3/secretary), and the appropriate set-focus messages are dispatched.

### Statefulness

There is some hairy and un-clojurery uses of state.

Firstly, the `pedestal-om-todo.state` namespace is intended to keep globally available state. For now, this is only used to keep a reference to the **Pedestal App's** input queue, which hopefully shouldn't change through the lifetime of the app.

Secondly, the use of Om requires that access to the **Om App's** state be available during the lifetime of that app. You will see that the List and Item views keep a reference to an `app-state` atom, whereby transformations are applied as needed.

### The Problem of Rendering Refresh when switching Focus

Say you are viewing the todo list with 2 items:

1. Take out the trash
2. Feed the cat

You click todo (1) and view its details.

Then you hit your browser back button to go back to the list without making any edits.

In this case, the todos have not changed, and hence no rendering deltas are produced. But you still need to refresh the todo list, or else no todos will be displayed ...

The way around this is for the app model to actually change, and force pedestal to emit the needed deltas.

Using the above example, we actually store todos under the path `[:todos :modify todo-id]`. We also store a particular value `[:todos :modify :req-id]`, which is a globally unique request id.

"Refreshing the list" is then a matter of changing the value at `[:todos :modify :req-id]`, which will cause the entire `[:todos :modify]` tree to be emitted and then used in rendering.

The same idea applies to switching filters and viewing items.

## Docs

Documentation can be generated using [Marginalia](https://github.com/gdeer81/marginalia) using the command:

```
lein marg app
```

## Stuff to Add (TODOs)

* actually figure out how to unit test the damn thing

* undo support as per [David Nolen's example](http://swannodette.github.io/todomvc/labs/architecture-examples/om-undo/index.html)

* persistence API

    This should just be an interface that can be pluggable into any adapter (localStorage, some database server, etc ...)

* externs file for React

    At this point in time, clojurescript advanced compilation is not supported, and therefore the `production` aspect does not yet work. (see [this section](https://github.com/pedestal/app-tutorial/wiki/Aspects) of the pedestal tutorial for an overview of aspects)

## License

Code is in the public domain.
