(ns vocationalops.phase
  "Rollout phases for the vocational education coordination actor.
  Controls what operations are available and how they're handled in each phase.")

(def phases
  {0 {:name "Read-Only"
      :description "All proposals held for review, no auto-commits"
      :auto #{} ; no auto-commits in phase 0
      :escalate #{:flag-safety-concern}}

   1 {:name "Enrollment Scheduling"
      :description "Course enrollment scheduling (approval-gated)"
      :auto #{} ; all still approval-gated in phase 1
      :escalate #{:flag-safety-concern}}

   2 {:name "Facility & Supply Coordination"
      :description "Add facility booking, supply requests, instructor shift proposals (approval-gated)"
      :auto #{} ; all still approval-gated in phase 2
      :escalate #{:flag-safety-concern}}

   3 {:name "High-Confidence Auto-Commit"
      :description "Auto-commit clean proposals, safety concerns always escalate"
      :auto #{:schedule-course-enrollment :coordinate-facility-booking
               :coordinate-supply-request :schedule-instructor-shift-proposal}
      :escalate #{:flag-safety-concern}}})

(defn phase-info
  "Get metadata for a given phase."
  [phase]
  (get phases phase))

(defn can-auto-commit?
  "Check if an operation can auto-commit in a given phase."
  [op-type phase]
  (let [phase-def (phase-info phase)]
    (boolean (and phase-def (contains? (:auto phase-def) op-type)))))

(defn always-escalates-in-phase?
  "Check if an operation always escalates in a given phase."
  [op-type phase]
  (let [phase-def (phase-info phase)]
    (boolean (and phase-def (contains? (:escalate phase-def) op-type)))))

(defn phase-available?
  "Check if a phase exists."
  [phase]
  (boolean (contains? phases phase)))

(defn current-phase-string
  "Human-readable description of current phase."
  [phase]
  (let [p (phase-info phase)]
    (if p
      (str "Phase " phase ": " (:name p))
      "Unknown phase")))
