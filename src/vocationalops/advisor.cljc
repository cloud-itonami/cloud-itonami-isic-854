(ns vocationalops.advisor
  "VocationalOpsAdvisor -- the LLM/reasoning layer for proposing coordination
  operations. The advisor reasons about administrative logistics but delegates
  all compliance & governance checks to the governor. Every proposal the advisor
  makes is tentative: it has an `:effect` (always `:propose` for this actor),
  a confidence score, and a rationale, but no authority to execute. Only the
  governor can authorize commitment or escalation.")

(def default-confidence 0.75)

(defn propose-enrollment-scheduling
  "Advisor proposes scheduling a course enrollment.
  Returns a proposal map with :effect :propose, :confidence, and :rationale."
  [enrollee-id course-id session-date]
  {:op :schedule-course-enrollment
   :effect :propose
   :enrollee-id enrollee-id
   :course-id course-id
   :session-date session-date
   :confidence default-confidence
   :summary (str "Propose enrolling " enrollee-id " in course " course-id)
   :rationale "Enrollment scheduling is within administrative coordination scope"
   :cites []})

(defn propose-facility-booking
  "Advisor proposes coordinating a facility booking."
  [enrollee-id facility-id booking-date duration-hours]
  {:op :coordinate-facility-booking
   :effect :propose
   :enrollee-id enrollee-id
   :facility-id facility-id
   :booking-date booking-date
   :duration-hours duration-hours
   :confidence default-confidence
   :summary (str "Propose booking facility " facility-id " for " enrollee-id)
   :rationale "Facility logistics coordination is within administrative scope"
   :cites []})

(defn propose-supply-request
  "Advisor proposes coordinating a supply request."
  [enrollee-id supply-list requested-date]
  {:op :coordinate-supply-request
   :effect :propose
   :enrollee-id enrollee-id
   :supply-list supply-list
   :requested-delivery-date requested-date
   :confidence default-confidence
   :summary (str "Propose supply request for " enrollee-id)
   :rationale "Supply coordination (non-instructional consumables) is within administrative scope"
   :cites []})

(defn propose-instructor-shift
  "Advisor proposes an instructor shift assignment (proposal only, never binding)."
  [enrollee-id instructor-id shift-date duration-hours]
  {:op :schedule-instructor-shift-proposal
   :effect :propose
   :enrollee-id enrollee-id
   :instructor-id instructor-id
   :shift-date shift-date
   :duration-hours duration-hours
   :confidence default-confidence
   :summary (str "Propose instructor assignment for course with " enrollee-id)
   :rationale "Administrative shift coordination is within scope; instructor competency/certification decisions are not"
   :cites []})

(defn flag-safety-concern
  "Advisor flags a safety concern for immediate escalation."
  [enrollee-id concern-type description severity]
  {:op :flag-safety-concern
   :effect :propose
   :enrollee-id enrollee-id
   :concern-type concern-type
   :description description
   :severity severity
   :confidence 0.9 ; high confidence for safety flagging
   :summary (str "Flag " severity " safety concern for " enrollee-id)
   :rationale "Safety concern requires immediate escalation to human oversight"
   :cites []})

(defn with-confidence
  "Update a proposal's confidence score."
  [proposal confidence]
  (assoc proposal :confidence confidence))

(defn with-rationale
  "Update a proposal's rationale."
  [proposal rationale]
  (assoc proposal :rationale rationale))

(defn with-cites
  "Add citations/references to a proposal."
  [proposal cites]
  (assoc proposal :cites cites))
