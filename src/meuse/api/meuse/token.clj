(ns meuse.api.meuse.token
  (:require [meuse.api.params :as params]
            [meuse.auth.password :as auth-password]
            [meuse.auth.request :as auth-request]
            [meuse.db.public.token :as public-token]
            [meuse.db.public.user :as public-user]
            [exoscale.ex :as ex]
            [clojure.tools.logging :refer [debug info error]]))

(defn delete-token
  [token-db request]
  (params/validate-params request ::delete)
  (let [token-name (get-in request [:body :name])
        user-name (get-in request [:body :user])
        auth-user-name (get-in request [:auth :user-name])]
    ;; the user or an admin can delete a token
    (when-not (or (= (get-in request [:auth :user-name])
                     user-name)
                  (auth-request/admin? request))
      (throw (ex/ex-forbidden
              (format "user %s cannot delete token for %s" auth-user-name user-name))))
    (info (format "deleting token %s for user %s" token-name user-name))
    (public-token/delete token-db
                         user-name
                         token-name)
    {:status 200
     :body {:ok true}}))

(defn create-token
  [user-db token-db request]
  (params/validate-params request ::create)
  (let [{:keys [name user password] :as body} (:body request)]
    (if-let [db-user (public-user/by-name user-db user)]
      (do (when-not (:users/active db-user)
            (throw (ex-info "user is not active"
                            {:type :exoscale.ex/forbidden})))
          (auth-password/check password (:users/password db-user))
          (info (format "creating token %s for user %s"
                        name
                        user))
          {:status 200
           :body {:token (public-token/create token-db
                                              (select-keys
                                               (:body request)
                                               [:name :user :validity]))}})
      (throw (ex/ex-not-found
              (format "the user %s does not exist" user))))))

(defn list-tokens
  [token-db request]
  (params/validate-params request ::list)
  (let [request-user (get-in request [:params :user])]
    (if request-user
      (auth-request/admin?-throw request)
      (auth-request/admin-or-tech?-throw request))
    (info "list tokens")
    (let [user-name (or request-user (get-in request [:auth :user-name]))
          tokens (->> (public-token/by-user token-db user-name)
                      (map #(select-keys % [:tokens/id
                                            :tokens/name
                                            :tokens/created_at
                                            :tokens/expired_at]))
                      (map #(clojure.set/rename-keys
                             %
                             {:tokens/id :id
                              :tokens/name :name
                              :tokens/created_at :created-at
                              :tokens/expired_at :expired-at})))]
      {:status 200
       :body {:tokens tokens}})))





