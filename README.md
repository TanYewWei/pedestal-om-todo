# pedestal-om-todo

This is just a toy experiment as an attempt to use [David Nolen's Om](https://github.com/swannodette/om) along side [Cognitect's Pedestal](https://github.com/pedestal/pedestal) to implement a [TodoMVC example](http://todomvc.com).

## TODO

* undo support as per [David Nolen's example](http://swannodette.github.io/todomvc/labs/architecture-examples/om-undo/index.html)

* routing based on focus of application.

    At present, we've got one focus for the todo list, and one focus for a detailed todo item view.

    So for example, the root `/` path should land us on the todo list, and the path `/item/:id` should show us the detailed view of a todo item

* persistence API

    This should just be an interface that can be pluggable into any adapter (localStorage, some database server, etc ...)

* externs file for React

    At this point in time, clojurescript advanced compilation is not supported 

## License

Code is in the public domain.
