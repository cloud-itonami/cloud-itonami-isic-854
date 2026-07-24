(ns vocationalops.operation
  "Operation definitions for the vocational education coordination actor,
  plus `execute-proposal!` -- the dispatcher that ties a proposal to the
  governor's HARD checks and the phase gate, and actually writes the
  outcome to the store. The governor (`vocationalops.governor`) only
  exposes individual violation-check functions; nothing previously
  aggregated them into a single decision or wired that decision back
  into a store mutation or audit-ledger fact, unlike every sibling
  actor's operation.cljc/StateGraph :commit node."
  (:require [vocationalops.governor :as governor]
            [vocationalops.phase :as phase]
            [vocationalops.store :as store]))

(def all-operations
  "Registry of all allowed operations with their schema and metadata."
  {:schedule-course-enrollment
   {:name "Schedule Course Enrollment"
    :description "Register enrollee for a specific course/session at a given date"
    :fields [:enrollee-id :course-id :session-date :duration-weeks]
    :phase-available-from 1}

   :coordinate-facility-booking
   {:name "Coordinate Facility Booking"
    :description "Book classroom/training room for course delivery"
    :fields [:enrollee-id :facility-id :booking-date :duration-hours]
    :phase-available-from 2}

   :coordinate-supply-request
   {:name "Coordinate Supply Request"
    :description "Request consumables (supplies, materials) for training facility"
    :fields [:enrollee-id :supply-list :requested-delivery-date]
    :phase-available-from 2}

   :schedule-instructor-shift-proposal
   {:name "Schedule Instructor Shift Proposal"
    :description "Propose instructor assignment for a course session (proposal only, requires approval)"
    :fields [:enrollee-id :instructor-id :shift-date :duration-hours]
    :phase-available-from 2}

   :flag-safety-concern
   {:name "Flag Safety Concern"
    :description "Escalate facility or participant wellbeing safety concern"
    :fields [:enrollee-id :concern-type :description :severity]
    :phase-available-from 1
    :always-escalates true}})

(defn validate-operation
  "Check if an operation is valid and has all required fields."
  [op-type proposal]
  (let [op-def (get all-operations op-type)]
    (cond
      (not op-def)
      {:valid false :errors [(str "Unknown operation: " op-type)]}

      (not (every? #(contains? proposal %) (:fields op-def)))
      {:valid false :errors ["Missing required fields for operation"]}

      :else
      {:valid true :errors []})))

(defn is-always-escalate?
  "Check if an operation always requires human escalation."
  [op-type]
  (let [op-def (get all-operations op-type)]
    (boolean (:always-escalates op-def))))

(defn available-in-phase?
  "Check if operation is available in a given phase."
  [op-type phase]
  (let [op-def (get all-operations op-type)
        min-phase (:phase-available-from op-def)]
    (when min-phase (>= phase min-phase))))

;; ----------------------------- dispatch (governor -> phase -> store) -----------------------------

(defn- all-violations [proposal st]
  (into [] (concat (governor/enrollee-unverified-violations proposal st)
                   (governor/effect-not-propose-violations proposal)
                   (governor/scope-exclusion-violations proposal))))

(defn execute-proposal!
  "Runs the three governor HARD checks and the phase gate for `proposal`
  (an advisor-shaped map with :op/:enrollee-id/:effect/...), then writes
  the REAL outcome to `store`:
    - phase disallows the op entirely -> {:status :rejected}, no store write
    - any governor violation (enrollee unverified / non-:propose effect /
      scope-excluded / op outside the allowlist) -> {:status :held ...},
      an audit-ledger hold fact, never committed
    - `is-always-escalate?` for this op, OR the phase can't auto-commit it
      -> {:status :escalated}, an audit-ledger approval-requested fact,
      never auto-committed -- see `approve-and-commit!` for the human step
    - otherwise -> {:status :committed :record ...}, the record is
      persisted via `store/commit-record!` and a committed fact is logged
  Returns a map with :status, :violations, and (when escalated) the
  original :proposal, so a caller can pass it to `approve-and-commit!`."
  [proposal st phase-num]
  (let [op (:op proposal)]
    (if-not (available-in-phase? op phase-num)
      {:status :rejected :reason "operation-not-allowed-in-phase" :phase phase-num :op op}
      (let [violations (all-violations proposal st)]
        (cond
          (seq violations)
          (do (store/append-ledger! st {:t :governor-hold :op op :enrollee-id (:enrollee-id proposal)
                                        :disposition :hold :violations violations})
              {:status :held :violations violations})

          (or (is-always-escalate? op) (not (phase/can-auto-commit? op phase-num)))
          (do (store/append-ledger! st
                {:t :approval-requested :op op :enrollee-id (:enrollee-id proposal) :disposition :escalate
                 :reason (if (is-always-escalate? op) "always-escalate-safety-concern" "phase-requires-approval")})
              {:status :escalated :proposal proposal})

          :else
          (let [record {:op op :enrollee-id (:enrollee-id proposal) :value (dissoc proposal :op)}]
            (store/commit-record! st record)
            (store/append-ledger! st {:t :committed :op op :enrollee-id (:enrollee-id proposal) :disposition :commit})
            {:status :committed :record record}))))))

(defn approve-and-commit!
  "A human operator approves a proposal previously escalated by
  `execute-proposal!` (its :proposal value); commits it now."
  [proposal st approved-by]
  (let [{:keys [op enrollee-id]} proposal
        record {:op op :enrollee-id enrollee-id :value (dissoc proposal :op)}]
    (store/commit-record! st record)
    (store/append-ledger! st {:t :approval-granted :op op :enrollee-id enrollee-id
                              :by approved-by :disposition :commit})
    record))
