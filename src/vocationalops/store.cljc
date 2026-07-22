(ns vocationalops.store
  "SSoT for the ISIC-854 other-education (adult vocational/continuing-ed)
  COORDINATION actor, behind a `Store` protocol so the backend is a swap, not
  a rewrite -- the same seam every `cloud-itonami-isic-*` actor in this fleet uses.

  This actor coordinates the back-office operations of adult vocational/continuing
  education providers: course enrollment/registration scheduling, facility/equipment
  booking coordination, supply coordination (training-room/office supplies), instructor
  shift proposals, and safety-concern flagging (facility hazards, wellbeing incidents).
  It never touches course-content decisions, curriculum, tutor-competency/certification,
  learner-progress/completion decisions, learner disciplinary action, or any
  safety-authority override -- see `vocationalops.governor`'s `scope-exclusion-violations`,
  a HARD, permanent, un-overridable block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/demo
  (no deps). An `enrollees` directory keyed by `:enrollee-id` STRING (never a
  keyword -- consistent keying from the start, avoiding the silent-miss
  bug that plagued earlier attempts).

  A registered/verified enrollee record must exist before ANY proposal
  for that enrollee may ever commit or escalate -- `vocationalops.governor`'s
  `enrollee-unverified-violations` re-derives this from the enrollee's own
  `:registered?`/`:verified?` fields, never from proposal self-report,
  the SAME 'ground truth, not self-report' discipline every sibling
  actor's own governor uses.

  The ledger stays append-only: which enrollee a proposal targeted, which
  operation, on what basis, committed/held/escalated and approved by
  whom is always a query over an immutable log.

  `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible EAV
  store (datalog q / pull / upsert). Pure `.cljc`, so it runs offline AND
  can be pointed at a real Datomic Local or a kotoba-server pod by
  swapping `langchain.db`'s `:db-api` (see `langchain.kotoba-db`) -- the
  same seam `cloud-itonami-isic-7810`'s `employmentops.store` and every
  other flagship-tier sibling actor's store uses. Both backends satisfy
  the SAME `Store` protocol and pass the same contract
  (`test/vocationalops_test.clj`), which is the whole point: the actor,
  `vocationalops.governor` and the audit ledger never know which SSoT
  they run on."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [langchain.db :as d]))

(defprotocol Store
  (enrollee [s enrollee-id] "Registered enrollee record, or nil.
    Enrollee map: {:enrollee-id .. :name .. :skill-level .. :registered? bool :verified? bool}.")
  (all-enrollees [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-enrollees [s enrollees] "replace/seed the enrollee directory (map enrollee-id->enrollee)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained enrollee directory covering both the happy path
  and the governor's own hard checks, so the actor + tests run offline."
  []
  {:enrollees
   {"enrollee-1" {:enrollee-id "enrollee-1" :name "Alice Smith" :skill-level "beginner"
                  :registered? true :verified? true}
    "enrollee-2" {:enrollee-id "enrollee-2" :name "Bob Johnson" :skill-level "intermediate"
                  :registered? true :verified? true}
    "enrollee-3" {:enrollee-id "enrollee-3" :name "Carol Lee" :skill-level "beginner"
                  :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (enrollee [_ enrollee-id] (get-in @a [:enrollees enrollee-id]))
  (all-enrollees [_] (sort-by :enrollee-id (vals (:enrollees @a))))
  (ledger [_] (:ledger @a))
  (coordination-log [_] (:coordination-log @a))
  (commit-record! [_ record]
    (swap! a update :coordination-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-enrollees [s enrollees] (when (seq enrollees) (swap! a assoc :enrollees enrollees)) s))

(defn seed-db
  "A MemStore seeded with the demo enrollee directory. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :coordination-log []))))

(defn mem-store
  "A MemStore seeded with an explicit `enrollees` map (enrollee-id string ->
  enrollee map) -- the primary test/dev entry point. `enrollees` may be empty
  (an unregistered-everywhere store)."
  [enrollees]
  (->MemStore (atom {:enrollees (or enrollees {}) :ledger [] :coordination-log []})))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (coordination-proposal records, ledger facts) are
  stored as EDN strings so `langchain.db` doesn't expand them into
  sub-entities -- the same convention every sibling actor's store uses."
  {:enrollee/id               {:db/unique :db.unique/identity}
   :ledger/seq                {:db/unique :db.unique/identity}
   :coordination-record/seq   {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- enrollee->tx [{:keys [enrollee-id name skill-level registered? verified?]}]
  (cond-> {:enrollee/id enrollee-id}
    name                  (assoc :enrollee/name name)
    skill-level           (assoc :enrollee/skill-level skill-level)
    (some? registered?)   (assoc :enrollee/registered? registered?)
    (some? verified?)     (assoc :enrollee/verified? verified?)))

(def ^:private enrollee-pull
  [:enrollee/id :enrollee/name :enrollee/skill-level :enrollee/registered? :enrollee/verified?])

(defn- pull->enrollee [m]
  (when (:enrollee/id m)
    {:enrollee-id (:enrollee/id m) :name (:enrollee/name m) :skill-level (:enrollee/skill-level m)
     :registered? (boolean (:enrollee/registered? m)) :verified? (boolean (:enrollee/verified? m))}))

(defrecord DatomicStore [conn]
  Store
  (enrollee [_ enrollee-id]
    (pull->enrollee (d/pull (d/db conn) enrollee-pull [:enrollee/id enrollee-id])))
  (all-enrollees [_]
    (->> (d/q '[:find [?id ...] :where [?e :enrollee/id ?id]] (d/db conn))
         (map #(pull->enrollee (d/pull (d/db conn) enrollee-pull [:enrollee/id %])))
         (sort-by :enrollee-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (coordination-log [_]
    (->> (d/q '[:find ?s ?r :where [?e :coordination-record/seq ?s] [?e :coordination-record/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (commit-record! [s record]
    (d/transact! conn [{:coordination-record/seq (count (coordination-log s))
                        :coordination-record/record (enc record)}])
    record)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-enrollees [s enrollees]
    (when (seq enrollees) (d/transact! conn (mapv enrollee->tx (vals enrollees))))
    s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `enrollees`
  (enrollee-id string -> enrollee map); empty when omitted."
  ([] (datomic-store {}))
  ([enrollees]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-enrollees s enrollees))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo enrollee directory -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (:enrollees (demo-data))))
