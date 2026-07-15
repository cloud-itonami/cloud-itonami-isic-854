(ns vocationalops-test
  (:require [clojure.test :refer [deftest is testing]]
            [vocationalops.store :as store]
            [vocationalops.governor :as governor]
            [vocationalops.phase :as phase]
            [vocationalops.advisor :as advisor]
            [vocationalops.operation :as op]
            [vocationalops.sim :as sim]))

(deftest store-basic
  (testing "MemStore creation and basic operations"
    (let [st (store/mem-store {"enrollee-1" {:enrollee-id "enrollee-1" :name "Alice" :registered? true :verified? true}})
          retrievals (store/enrollee st "enrollee-1")]
      (is (= "Alice" (:name retrievals)))
      (is (true? (:verified? retrievals))))))

(deftest store-all-enrollees
  (testing "Listing all enrollees"
    (let [enrollees {"e1" {:enrollee-id "e1" :name "A" :registered? true :verified? true}
                     "e2" {:enrollee-id "e2" :name "B" :registered? true :verified? true}}
          st (store/mem-store enrollees)]
      (is (= 2 (count (store/all-enrollees st)))))))

(deftest store-ledger
  (testing "Append-only ledger operations"
    (let [st (store/mem-store {})]
      (store/append-ledger! st {:event "test" :ts "2026-07-15"})
      (is (= 1 (count (store/ledger st)))))))

(deftest governor-enrollee-unverified
  (testing "Unverified enrollee check"
    (let [st (store/mem-store {"e1" {:enrollee-id "e1" :registered? true :verified? false}})
          prop {:enrollee-id "e1" :op :schedule-course-enrollment :effect :propose}
          violations (governor/enrollee-unverified-violations prop st)]
      (is (= 1 (count violations)))
      (is (= :enrollee-unverified (:rule (first violations)))))))

(deftest governor-verified-enrollee-passes
  (testing "Verified enrollee passes check"
    (let [st (store/mem-store {"e1" {:enrollee-id "e1" :registered? true :verified? true}})
          prop {:enrollee-id "e1" :op :schedule-course-enrollment :effect :propose}
          violations (governor/enrollee-unverified-violations prop st)]
      (is (empty? violations)))))

(deftest governor-effect-not-propose
  (testing "Effect not :propose check"
    (let [prop {:effect :commit}
          violations (governor/effect-not-propose-violations prop)]
      (is (= 1 (count violations)))
      (is (= :effect-not-propose (:rule (first violations)))))))

(deftest governor-effect-propose-passes
  (testing "Effect :propose passes check"
    (let [prop {:effect :propose}
          violations (governor/effect-not-propose-violations prop)]
      (is (empty? violations)))))

(deftest governor-scope-exclusion
  (testing "Scope exclusion check catches curriculum keywords"
    (let [prop {:op :schedule-course-enrollment
                :effect :propose
                :summary "Modify the curriculum for advanced learners"
                :rationale "Need better lesson planning"
                :cites []
                :value {}}
          violations (governor/scope-exclusion-violations prop)]
      (is (= 1 (count violations)))
      (is (= :scope-excluded (:rule (first violations)))))))

(deftest governor-scope-exclusion-pedagogy
  (testing "Scope exclusion catches pedagogical keywords"
    (let [prop {:op :coordinate-supply-request
                :effect :propose
                :summary "Request instructional materials and pedagogical resources"
                :rationale "Pedagogy improvements needed"
                :cites []
                :value {}}
          violations (governor/scope-exclusion-violations prop)]
      (is (= 1 (count violations))))))

(deftest governor-allowed-op-check
  (testing "Valid operation passes check"
    (let [prop {:op :schedule-course-enrollment
                :effect :propose
                :summary "Enroll in course"
                :rationale "Admin coordination"
                :cites []
                :value {}}
          violations (governor/scope-exclusion-violations prop)]
      (is (empty? violations)))))

(deftest governor-disallowed-op
  (testing "Invalid operation is rejected"
    (let [prop {:op :grade-learner-performance
                :effect :propose
                :summary "Grade the learner"
                :rationale "Assessment"
                :cites []
                :value {}}
          violations (governor/scope-exclusion-violations prop)]
      (is (= 1 (count violations)))
      (is (= :op-not-allowed (:rule (first violations)))))))

(deftest phase-info
  (testing "Phase metadata retrieval"
    (let [p0 (phase/phase-info 0)
          p3 (phase/phase-info 3)]
      (is (= "Read-Only" (:name p0)))
      (is (= "High-Confidence Auto-Commit" (:name p3))))))

(deftest phase-auto-commit
  (testing "Auto-commit eligibility by phase"
    (is (false? (phase/can-auto-commit? :schedule-course-enrollment 0)))
    (is (false? (phase/can-auto-commit? :schedule-course-enrollment 2)))
    (is (true? (phase/can-auto-commit? :schedule-course-enrollment 3)))))

(deftest phase-always-escalates
  (testing "Safety concerns always escalate"
    (is (true? (phase/always-escalates-in-phase? :flag-safety-concern 0)))
    (is (true? (phase/always-escalates-in-phase? :flag-safety-concern 3)))))

(deftest advisor-proposal-creation
  (testing "Advisor can create valid proposals"
    (let [prop (advisor/propose-enrollment-scheduling "e1" "course1" "2026-07-20")]
      (is (= :schedule-course-enrollment (:op prop)))
      (is (= :propose (:effect prop)))
      (is (= "e1" (:enrollee-id prop)))
      (is (>= (:confidence prop) 0)))))

(deftest advisor-safety-concern
  (testing "Safety concerns have high confidence"
    (let [prop (advisor/flag-safety-concern "e1" "hazard" "Broken equipment" "high")]
      (is (= :flag-safety-concern (:op prop)))
      (is (>= (:confidence prop) 0.9)))))

(deftest operation-schema
  (testing "Operation definitions exist"
    (let [enroll-op (get op/all-operations :schedule-course-enrollment)]
      (is (not-nil enroll-op))
      (is (= "Schedule Course Enrollment" (:name enroll-op))))))

(deftest operation-validate
  (testing "Operation validation"
    (let [valid-prop {:op :schedule-course-enrollment
                      :enrollee-id "e1"
                      :course-id "c1"
                      :session-date "2026-07-20"
                      :duration-weeks 4}
          result (op/validate-operation :schedule-course-enrollment valid-prop)]
      (is (true? (:valid result))))))

(deftest sim-demo-runs
  (testing "Simulation demo completes without error"
    (is (nil? (sim/demo-run)))))
