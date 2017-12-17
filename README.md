# trail

Ring routing library

## Install

[Looking for v1? Look no further my friend!](https://github.com/swlkr/trail/tree/1.13.0)

Add this to your `project.clj`

```clojure
[trail "2.0.0"]
```

## Usage

```clojure
(ns your-app.core
  (require [trail.core :as trail]))
```

These are the routes

```clojure
(def routes [
  [:get    "/items"           items/index]
  [:get    "/items/:id"       items/show]
  [:get    "/items/:id/fresh" items/fresh]
  [:get    "/items/:id/edit"  items/edit]
  [:post   "/items"           items/create]
  [:put    "/items/:id"       items/change]
  [:delete "/items/:id"       items/delete]
])
```

And you can write your routes as data if you wanted to.
Or there are some functions if you prefer parens

```clojure
(def routes
  (-> (trail/get    "/items"           items/index)
      (trail/get    "/items/:id"       items/show)
      (trail/get    "/items/:id/fresh" items/fresh)
      (trail/get    "/items/:id/edit"  items/edit)
      (trail/post   "/items"           items/create)
      (trail/put    "/items/:id"       items/change)
      (trail/delete "/items/:id"       items/delete)))
```

There's also a function that turns your route into a ring
handler function

`(trail/match-routes your-route-map)`

Here's a more complete example

```clojure
(ns your-app.core
  (require [trail.core :as trail]
           [org.httpkit.server :as server]
           [ring.middleware.defaults :as ring-defaults]
           [your-app.controllers.items :as items]))

(def routes
  (-> (trail/get    "/items"           items/index)
      (trail/get    "/items/:id"       items/show)
      (trail/get    "/items/:id/fresh" items/fresh)
      (trail/get    "/items/:id/edit"  items/edit)
      (trail/post   "/items"           items/create)
      (trail/put    "/items/:id"       items/change)
      (trail/delete "/items/:id"       items/delete)))

(def app
  (-> (trail/match-routes routes) ; it's this one here
      (ring-defaults/wrap-defaults site-defaults)))

(server/run-server app {:port 1337})
```

The last thing this library has that other routing
libraries do not is the concept of a `resource`
shamelessly stolen from rails routing

So instead of this

```clojure
(ns your-app.core
  (require [trail.core :as trail]
           [your-app.controllers.items :as items]))

(def routes
  (-> (trail/get    "/items"           items/index)
      (trail/get    "/items/:id"       items/show)
      (trail/get    "/items/:id/fresh" items/fresh)
      (trail/get    "/items/:id/edit"  items/edit)
      (trail/post   "/items"           items/create)
      (trail/put    "/items/:id"       items/change)
      (trail/delete "/items/:id"       items/delete)))
```

You can do this

```clojure
(ns your-app.core
  (require [trail.core :as trail]
           [your-app.controllers.items :as items]))

(def routes
  (-> (trail/resource :items)))
```

And you can do this

```clojure
(ns your-app.core
  (:require [trail.core :as trail]
            [your-app.controllers.posts :as posts]
            [your-app.controllers.tags :as tags]))

(def routes
  (-> (trail/resource :posts :tags))

; this gives you
;
; GET "/posts/:post-id/tags" => tags/index
; GET "/posts/:post-id/tags/:id" => tags/show
; GET "/posts/:post-id/tags/new" => tags/fresh
; GET "/posts/:post-id/tags/:id/edit" => tags/edit
; POST "/posts/:post-id/tags" => tags/create
; PUT "/posts/:post-id/tags/:id" => tags/change
; DELETE "/posts/:post-id/tags/:id" => tags/delete
```

Don't want all of the routes a resource gives you?

```clojure
(def routes
 (-> (trail/resource :posts :only [:index :show])))
 
; =>
[
 [:get "/posts"     posts/index]
 [:get "/posts/:id" posts/show]
]
```

Want all of the route except certain ones?

```clojure
(def routes
 (-> (trail/resource :posts :except [:index :show])))
 
 ; =>
 [
  [:get    "/posts/:id/fresh" posts/fresh]
  [:get    "/posts/:id/edit"  posts/edit]
  [:post   "/posts"           posts/create]
  [:put    "/posts/:id"       posts/change]
  [:delete "/posts/:id"       posts/delete]
 ]
```

# Why?

I like shortcuts, so rails resource routing really appealed to me. I also like clojure
so I combined them.
