(ns vocationalops.sim
  "Simulation of a complete vocational education actor operation.
  Runs a proposal through the full pipeline: advisor → governor → phase → ledger."
  (:require [vocationalops.store :as store]
            [vocationalops.governor :as governor]
            [vocationalops.phase :as phase]
            [vocationalops.advisor :as advisor]
            [vocationalops.operation :as op]))

(def sim-state
  "Simulation state atom: tracks current phase and store state."
  (atom {:phase 2
         :store (store/seed-db)
         :proposal-id 0}))

(defn next-proposal-id!
  "Get next proposal ID and increment counter."
  []
  (let [id (swap! sim-state update :proposal-id inc)]
    id))

(defn run-proposal
  "Execute a proposal through the full actor pipeline.
  Returns a result map with :status (HOLD/ESCALATE/COMMIT), :reasons, and ledger entries."
  [proposal]
  (let [st (:store @sim-state)
        current-phase (:phase @sim-state)
        p-id (next-proposal-id!)

        ;; Run all hard checks
        unverified (governor/enrollee-unverified-violations proposal st)
        effect-check (governor/effect-not-propose-violations proposal)
        scope-check (governor/scope-exclusion-violations proposal)
        all-violations (concat unverified effect-check scope-check)

        ;; Determine outcome
        has-hard-violations (not-empty all-violations)
        confidence (get proposal :confidence 0.5)
        confidence-below-floor (< confidence governor/confidence-floor)
        always-escalates (governor/always-escalate-ops (:op proposal))

        status (cond
                 has-hard-violations :hold
                 (or always-escalates confidence-below-floor) :escalate
                 (phase/can-auto-commit? (:op proposal) current-phase) :commit
                 :else :hold)

        ledger-entry {:id p-id
                      :timestamp "2026-07-15T00:00:00Z"
                      :enrollee-id (:enrollee-id proposal)
                      :op (:op proposal)
                      :status status
                      :confidence confidence}]

    ;; Log to ledger
    (store/append-ledger! st ledger-entry)

    ;; If committed, also log to coordination log
    (when (= status :commit)
      (store/commit-record! st (assoc ledger-entry :committed true)))

    {:proposal-id p-id
     :status status
     :confidence confidence
     :violation-count (count all-violations)
     :has-hard-violations has-hard-violations
     :phase current-phase}))

(defn demo-run
  "Run a complete demo scenario through the actor pipeline."
  []
  (reset! sim-state {:phase 2
                     :store (store/seed-db)
                     :proposal-id 0})

  (let [store-inst (:store @sim-state)]
    (println "\n=== VocationalOps Actor Demo ===\n")

    ;; Demo 1: Happy path - valid enrollment proposal
    (println "Demo 1: Valid course enrollment proposal")
    (let [prop (advisor/propose-enrollment-scheduling
                 "enrollee-1" "driving-course-2026" "2026-07-20")
          result (run-proposal prop)]
      (println "  Status:" (:status result))
      (println "  Violations:" (:violation-count result))
      (println "  Phase:" (:phase result))
      (println "  Expected: HOLD in phase 2 (enrollment-only gated)\n"))

    ;; Demo 2: Unverified enrollee
    (println "Demo 2: Proposal for unverified enrollee")
    (let [prop (advisor/propose-enrollment-scheduling
                 "enrollee-3" "driving-course-2026" "2026-07-20")
          result (run-proposal prop)]
      (println "  Status:" (:status result))
      (println "  Violations:" (:violation-count result))
      (println "  Expected: HOLD (enrollee-3 is not verified)\n"))

    ;; Demo 3: Safety concern (always escalates)
    (println "Demo 3: Safety concern flagging (always escalates)")
    (let [prop (advisor/flag-safety-concern
                 "enrollee-1" "facility-hazard" "Broken chair in training room" "high")
          result (run-proposal prop)]
      (println "  Status:" (:status result))
      (println "  Violations:" (:violation-count result))
      (println "  Expected: ESCALATE (safety concerns always escalate)\n"))

    ;; Demo 4: Scope violation - curriculum content
    (println "Demo 4: Scope violation - attempting curriculum decision")
    (let [prop (assoc (advisor/propose-enrollment-scheduling
                       "enrollee-1" "course-1" "2026-07-20")
                 :summary "Modify curriculum design for advanced learners"
                 :rationale "Updating lesson plan pedagogy")
          result (run-proposal prop)]
      (println "  Status:" (:status result))
      (println "  Violations:" (:violation-count result))
      (println "  Expected: HOLD (curriculum/pedagogy is scope-excluded)\n"))

    ;; Print summary
    (println "=== Audit Ledger Summary ===")
    (let [ledger (store/ledger store-inst)]
      (println (str "Total entries: " (count ledger)))
      (println "Entries logged successfully."))

    nil))
