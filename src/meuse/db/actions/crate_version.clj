(ns meuse.db.actions.crate-version
  (:require [meuse.db.actions.crate :as crate-db]
            [meuse.db.queries.crate-version :as crate-version-queries]
            [meuse.message :refer [yanked?->msg]]
            [next.jdbc :as jdbc]
            [clojure.tools.logging :refer [debug info error]])
  (:import java.util.UUID))

(defn update-yank
  "Updates the `yanked` field in the database for a crate version."
  [database crate-name crate-version yanked?]
  (info (yanked?->msg yanked?) "crate" crate-name crate-version)
  (jdbc/with-transaction [db-tx database]
    (if-let [crate (crate-db/by-name-and-version db-tx crate-name crate-version)]
      (do
        (when-not (:crates_versions/version crate)
          (throw
           (ex-info
            (format "cannot %s the crate: the version does not exist"
                    (yanked?->msg yanked?))
            {:type :meuse.error/not-found
             :crate-name crate-name
             :crate-version crate-version})))
        (when (= yanked? (:crates_versions/yanked crate))
          (throw
           (ex-info
            (format "cannot %s the crate: crate state is already %s"
                    (yanked?->msg yanked?)
                    (yanked?->msg yanked?))
            {:type :meuse.error/incorrect
             :crate-name crate-name
             :crate-version crate-version})))
        (jdbc/execute! db-tx (crate-version-queries/update-yanked
                              (:crates_versions/id crate)
                              yanked?)))
      (throw (ex-info (format "cannot %s the crate: the crate does not exist"
                              (yanked?->msg yanked?))
                      {:type :meuse.error/not-found
                       :crate-name crate-name
                       :crate-version crate-version})))))

(defn last-updated
  "Get the last n updated versions"
  [database n]
  (->> (jdbc/execute! database (crate-version-queries/last-updated n))))

(defn count-crates-versions
  "Count crates versions"
  [database]
  (-> (jdbc/execute! database (crate-version-queries/count-crates-versions))
      first))

(defn inc-download
  [database crate-name version]
  (if-let [crate (crate-db/by-name-and-version database crate-name version)]
    (-> (jdbc/execute! database (crate-version-queries/inc-download
                                 (:crates_versions/id crate))))
    (throw (ex-info (format "crate %s version %s not found"
                            crate-name version)
                    {:type :meuse.error/not-found}))))

