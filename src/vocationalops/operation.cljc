(ns vocationalops.operation
  "Operation definitions for the vocational education coordination actor.
  Each operation is a closed proposal type with its own validation rules.")

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
