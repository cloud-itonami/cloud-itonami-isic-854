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
  whom is always a query over an immutable log.")

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
