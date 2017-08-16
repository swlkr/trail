# trail

Ring routing library

## Install

```bash
lein plz trail
```

## Usage

```clojure
(ns your-proj.core
  (require [trail.core :as trail])

(defn get-org-teams [request])

(trail/route {:method :get
              :uri "/organizations/:org-id/teams/:team-id"
              :fn get-org-teams})

(trail/match-route {:request-method :get :uri "/organizations/1/teams/2"})
; => {:fn get-org-teams :params {:org-id 1 :team-id 2}}
```
