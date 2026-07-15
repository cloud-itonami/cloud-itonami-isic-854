(ns vocationalops.governor
  "VocationalOpsGovernor -- the independent compliance layer for adult vocational
  education administrative coordination. The advisor has no notion of whether an
  enrollee is actually registered and verified in the provider's roster, whether its
  own proposed `:effect` secretly claims a direct actuation instead of a mere proposal,
  or whether it has silently drifted into a permanently out-of-scope decision area, so
  this MUST be a separate system able to *reject* a proposal and fall back to HOLD.

  This actor's scope is ADMINISTRATIVE COORDINATION ONLY (course enrollment scheduling,
  facility booking, supply coordination, instructor shift proposals, safety-concern
  flagging). It NEVER performs or authorizes:
    - course-content or curriculum decisions
    - instructional-materials selection
    - tutor/instructor competency, qualification, or certification determinations
    - learner progress, assessment, or completion decisions
    - learner disciplinary action or enrollment termination
    - safety-authority overrides (mandatory reporting, law-enforcement liaison)

  Three HARD checks, ALL permanent, un-overridable by any human approval:

    1. Enrollee unverified     -- the target enrollee record must exist
                                 AND be independently confirmed
                                 `:registered?`/`:verified?` in the
                                 store before ANY proposal for it may
                                 commit or even escalate. Never trusts
                                 a proposal's own claim about the
                                 enrollee -- re-derived from the
                                 enrollee's own store record, the same
                                 'ground truth, not self-report'
                                 discipline every sibling actor's
                                 governor uses.
    2. Effect not :propose    -- every proposal's `:effect` MUST be
                                 `:propose`. Any other effect value
                                 is, by construction, a claim to
                                 directly actuate/commit outside
                                 governance -- HARD block, not
                                 merely low-confidence.
    3. Scope exclusion        -- ANY proposal (regardless of op)
                                 whose op, rationale, summary,
                                 citations or draft value touches
                                 course-content/curriculum/
                                 instructional-materials/tutor-
                                 competency/learner-progress/
                                 learner-discipline/safety-authority
                                 territory is a HARD, PERMANENT block
                                 -- this actor's charter excludes that
                                 territory structurally, not as a
                                 rollout milestone. Evaluated
                                 UNCONDITIONALLY on every proposal.
                                 An op outside the closed five-op
                                 allowlist is the SAME failure mode
                                 (an advisor proposing something it
                                 was never authorized to propose) and
                                 is folded into this same check.
                                 Legitimate safety-concern flagging
                                 is never itself blocked.

  One ESCALATE (SOFT) gate: LLM confidence below the floor, OR the op
  is `:flag-safety-concern` -- ALWAYS escalates to a human, regardless
  of confidence, regardless of how clean the proposal otherwise is.
  `vocationalops.phase` independently agrees: `:flag-safety-concern` is
  never a member of any phase's `:auto` set either -- two layers, not one."
  (:require [clojure.string :as str]
            [vocationalops.store :as store]))

(def confidence-floor 0.6)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a
  scope violation by construction (see `scope-exclusion-violations`)."
  #{:schedule-course-enrollment :coordinate-facility-booking
    :coordinate-supply-request :schedule-instructor-shift-proposal
    :flag-safety-concern})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not."
  #{:flag-safety-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as touching a
  permanently out-of-scope decision area -- course-content, curriculum,
  instructional-materials, tutor-competency, learner-progress, learner
  discipline, or safety-authority enforcement beyond escalation. Scanned
  across the proposal's op/summary/rationale/cites/value, never trusting
  the advisor's own framing of its intent. Qualified terms to avoid false
  blocks of legitimate safety-concern flagging."
  ["course content" "course-content" "コース内容" "curriculum" "カリキュラム"
   "pedagogical" "教育学的" "lesson plan" "lesson-plan" "授業計画"
   "course design" "course-design" "コース設計" "instructional" "教育用"
   "tutor" "チューター" "instructor qualifi" "instructor-qualifi" "講師資格"
   "certification" "認定資格" "competency" "能力要件" "learner assess" "学習者評価"
   "learner progress" "learner-progress" "学習進捗" "completion" "修了" "grade"
   "成績" "grading" "採点" "evaluation" "評価" "discipline" "規律"
   "disciplinary action" "disciplinary-action" "懲罰処分" "expulsion" "退学"
   "suspension" "停学" "behavior" "行動" "conduct" "品行" "child protect"
   "児童保護" "law enforcement" "law-enforcement" "警察" "police" "cps" "cas"
   "mandatory report" "mandatory-report" "虐待報告"])

;; ----------------------------- checks -----------------------------

(defn- enrollee-unverified-violations
  "The target enrollee must exist AND be independently `:registered?`/
  `:verified?` in the store -- never trust the proposal's own
  `:enrollee-id` claim without a store lookup."
  [{:keys [enrollee-id]} st]
  (let [e (store/enrollee st enrollee-id)]
    (when-not (and e (:registered? e) (:verified? e))
      [{:rule :enrollee-unverified
        :detail (str enrollee-id " は未登録または未検証の受講者 -- いかなる提案も進められない")}])))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim
  to directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one
  lower-cased blob the scope-exclusion scan checks."
  [proposal]
  (str/lower-case (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist,
  or one whose content touches course-content/curriculum/instructional-
  materials/tutor-competency/learner-progress/learner-discipline/
  safety-authority territory, regardless of confidence or how clean
  every other check is. Evaluated UNCONDITIONALLY on every proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "コース内容/カリキュラム/教材選定/講師資格/学習進捗/受講者懲罰/安全当局の領域に触れる提案は永久に禁止"}])))