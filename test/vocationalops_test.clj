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

(deftest advisor-supply-request-rationale-does-not-self-block
  ;; regression: propose-supply-request's own rationale used to say
  ;; "non-instructional consumables" -- the substring "instructional"
  ;; is one of governor's scope-excluded-terms, so the advisor's own
  ;; correct within-scope description of the SAME clean proposal false-
  ;; triggered a HARD scope-exclusion hold, discovered by actually
  ;; running the full execute-proposal! pipeline (not caught by any
  ;; unit test that only exercises store/governor/advisor in isolation).
  (let [prop (advisor/propose-supply-request "e1" ["safety goggles"] "2026-08-05")]
    (is (empty? (governor/scope-exclusion-violations prop))
        "the advisor's own rationale for this in-scope op must never self-trigger scope-exclusion")))

(deftest advisor-safety-concern
  (testing "Safety concerns have high confidence"
    (let [prop (advisor/flag-safety-concern "e1" "hazard" "Broken equipment" "high")]
      (is (= :flag-safety-concern (:op prop)))
      (is (>= (:confidence prop) 0.9)))))

(deftest operation-schema
  (testing "Operation definitions exist"
    (let [enroll-op (get op/all-operations :schedule-course-enrollment)]
      (is (not (nil? enroll-op)))
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

;; ----------------------------- backend parity (MemStore vs DatomicStore) -----------------------------
;; Proves `DatomicStore` satisfies the SAME `Store` protocol contract as `MemStore` --
;; the same pattern `cloud-itonami-isic-7810`'s `employmentops.store-contract-test` uses.

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Alice Smith" (:name (store/enrollee s "enrollee-1"))))
      (is (true? (:registered? (store/enrollee s "enrollee-1"))))
      (is (true? (:verified? (store/enrollee s "enrollee-1"))))
      (is (false? (:verified? (store/enrollee s "enrollee-3"))) "enrollee-3 is registered but unverified")
      (is (nil? (store/enrollee s "no-such-enrollee")))
      (is (= ["enrollee-1" "enrollee-2" "enrollee-3"] (mapv :enrollee-id (store/all-enrollees s))))
      (is (= [] (store/ledger s)))
      (is (= [] (store/coordination-log s))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "commit-record! appends to coordination-log"
        (store/commit-record! s {:op :schedule-course-enrollment :enrollee-id "enrollee-1" :value {:course "c1"}})
        (is (= 1 (count (store/coordination-log s))))
        (is (= "enrollee-1" (:enrollee-id (first (store/coordination-log s))))))
      (testing "append-ledger! is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/enrollee s "nope")))
    (is (= [] (store/all-enrollees s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/coordination-log s)))
    (store/with-enrollees s {"x" {:enrollee-id "x" :name "New Enrollee" :skill-level "beginner"
                                  :registered? true :verified? false}})
    (is (= "New Enrollee" (:name (store/enrollee s "x"))))))

;; ----------------------------- execute-proposal! (real store wiring) -----------------------------

(deftest execute-proposal-commits-clean-proposal-at-phase-3
  (let [s (store/seed-db)
        proposal (advisor/propose-enrollment-scheduling "enrollee-1" "course-1" "2026-08-01")
        result (op/execute-proposal! proposal s 3)]
    (is (= :committed (:status result)))
    (is (= 1 (count (store/coordination-log s))))
    (is (= 1 (count (store/ledger s))))
    (is (= :committed (:t (first (store/ledger s)))))))

(deftest execute-proposal-always-escalates-safety-concern-even-at-phase-3
  (let [s (store/seed-db)
        proposal (advisor/flag-safety-concern "enrollee-1" "facility-hazard" "loose wiring in workshop" "high")
        result (op/execute-proposal! proposal s 3)]
    (is (= :escalated (:status result)))
    (is (= 0 (count (store/coordination-log s))) "never auto-commits")
    (is (= :approval-requested (:t (first (store/ledger s)))))
    (is (= "always-escalate-safety-concern" (:reason (first (store/ledger s)))))))

(deftest execute-proposal-requires-approval-below-phase-3
  (let [s (store/seed-db)
        proposal (advisor/propose-enrollment-scheduling "enrollee-1" "course-1" "2026-08-01")
        result (op/execute-proposal! proposal s 1)]
    (is (= :escalated (:status result)))
    (is (= 0 (count (store/coordination-log s))))
    (is (= "phase-requires-approval" (:reason (first (store/ledger s)))))))

(deftest execute-proposal-holds-unverified-enrollee
  (let [s (store/seed-db)
        proposal (advisor/propose-facility-booking "enrollee-3" "facility-1" "2026-08-01" 2)
        result (op/execute-proposal! proposal s 2)]
    (is (= :held (:status result)))
    (is (= 0 (count (store/coordination-log s))))
    (is (= :governor-hold (:t (first (store/ledger s)))))
    (is (= :enrollee-unverified (:rule (first (:violations result)))))))

(deftest execute-proposal-rejects-when-phase-disallows-op
  (let [s (store/seed-db)
        proposal (advisor/propose-facility-booking "enrollee-1" "facility-1" "2026-08-01" 2)
        result (op/execute-proposal! proposal s 1)]
    (is (= :rejected (:status result)))
    (is (= 0 (count (store/ledger s))) "no store write at all when the phase blocks the op")))

(deftest execute-proposal-holds-scope-excluded-content
  (let [s (store/seed-db)
        proposal (-> (advisor/propose-supply-request "enrollee-1" ["textbooks"] "2026-08-01")
                     (advisor/with-rationale "This actually concerns the course curriculum design"))
        result (op/execute-proposal! proposal s 3)]
    (is (= :held (:status result)))
    (is (= :scope-excluded (:rule (first (:violations result)))))))

(deftest approve-and-commit-persists-an-escalated-proposal
  (let [s (store/seed-db)
        proposal (advisor/flag-safety-concern "enrollee-1" "facility-hazard" "loose wiring" "high")
        escalated (op/execute-proposal! proposal s 3)
        record (op/approve-and-commit! (:proposal escalated) s "op-1")]
    (is (= "enrollee-1" (:enrollee-id record)))
    (is (= 1 (count (store/coordination-log s))))
    (is (= 2 (count (store/ledger s))) "approval-requested + approval-granted")
    (is (= :approval-granted (:t (last (store/ledger s)))))
    (is (= "op-1" (:by (last (store/ledger s)))))))
