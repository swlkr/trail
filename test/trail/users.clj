(ns trail.users)

(defn index [request]
  "GET /users")

(defn new! [request]
  "GET /users/new")

(defn create [request]
  "POST /users")

(defn edit [request]
  "GET /users/:id/edit")

(defn update! [request]
  "PUT /users/:id")

(defn delete [request]
  (str "DELETE /users/" (-> request :params :id)))
