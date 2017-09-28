# trail

Ring routing library

## Install

Add this to your `project.clj`

```clojure
[trail "1.1.0"]
```

## Usage

A route map is organized like this

```clojure
(def routes {
  [:get "/items"]          items/index
  [:get "/items/:id"]      items/show
  [:get "/items/:id/new"]  items/new!
  [:get "/items/:id/edit"] items/edit
  [:post "/items"]         items/create
  [:put "/items/:id"]      items/update
  [:delete "/items/:id"]   items/delete
})
```

And you can write your routes as data if you wanted to.
But there are some functions that make it a little nicer

```clojure
(def routes
  (-> (trail/get "/items"          items/index)
      (trail/get "/items/:id"      items/show)
      (trail/get "/items/:id/new"  items/new!)
      (trail/get "/items/:id/edit" items/edit)
      (trail/post "/items"         items/create)
      (trail/put "/items/:id"      items/update)
      (trail/delete "/items/:id"   items/delete)))
```

There's also a function that actually does the mapping

```clojure
(ns your-app.core
  (require [trail.core :as trail]
           [org.httpkit.server :as server]
           [ring.middleware.defaults :as ring-defaults]
           [your-app.controllers.items :as items]))

(def routes
  (-> (trail/get "/items"          items/index)
      (trail/get "/items/:id"      items/show)
      (trail/get "/items/:id/new"  items/new!)
      (trail/get "/items/:id/edit" items/edit)
      (trail/post "/items"         items/create)
      (trail/put "/items/:id"      items/update)
      (trail/delete "/items/:id"   items/delete)))

(def app
  (-> (trail/match-routes routes)
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
  (-> (trail/get "/items"          items/index)
      (trail/get "/items/:id"      items/show)
      (trail/get "/items/:id/new"  items/new!)
      (trail/get "/items/:id/edit" items/edit)
      (trail/post "/items"         items/create)
      (trail/put "/items/:id"      items/update)
      (trail/delete "/items/:id"   items/delete)))
```

You can do this

```clojure
(ns your-app.core
  (require [trail.core :as trail]
           [your-app.controllers.items :as items]))
           [your-app.controllers.tags :as tags]))

(def routes
  (-> {}
      (trail/resource :items)
      (trail/resource :tags)))
```
