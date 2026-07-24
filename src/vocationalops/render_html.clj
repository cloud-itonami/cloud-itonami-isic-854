(ns vocationalops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300
  pattern, adapted from cloud-itonami-isic-851's own
  `schoolops.render-html` for this repo's DIFFERENT actor architecture
  -- no langgraph StateGraph here, just `vocationalops.advisor` +
  `vocationalops.governor` + the new
  `vocationalops.operation/execute-proposal!` dispatcher added
  alongside this generator). Drives the REAL actor stack through a
  scenario built from the actor's OWN seeded demo data
  (`vocationalops.store/seed-db`, enrollees
  enrollee-1/enrollee-2/enrollee-3) and renders the result
  deterministically -- no invented numbers, no timestamps in the page
  content, byte-identical across reruns against the same seed.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [vocationalops.store :as store]
            [vocationalops.advisor :as advisor]
            [vocationalops.operation :as op]))

(defn run-demo!
  "Runs a fresh seeded store through a scenario covering every distinct
  `execute-proposal!` outcome: enrollee-1 clears three ops cleanly at
  phase-3 (all :committed); enrollee-1's safety-concern flag ALWAYS
  escalates (never auto-commits, even at phase-3) and is then approved
  by a human operator; enrollee-3 (registered but NOT `:verified?` in
  the seed data) HARD-holds on `:enrollee-unverified` -- never reaches
  a human; a supply request whose advisor rationale drifted into
  curriculum-design territory HARD-holds on `:scope-excluded` -- also
  never reaches a human; an enrollment-scheduling request at phase-0
  (read-only) is rejected by the phase gate before the governor is even
  consulted. Returns the resulting store -- every field `render` below
  reads is real governor/store output, not a hand-typed copy."
  []
  (let [db (store/seed-db)]
    (op/execute-proposal! (advisor/propose-enrollment-scheduling "enrollee-1" "course-1" "2026-08-01") db 3)
    (op/execute-proposal! (advisor/propose-facility-booking "enrollee-1" "shop-1" "2026-08-03" 3) db 3)
    (op/execute-proposal! (advisor/propose-supply-request "enrollee-1" ["safety goggles"] "2026-08-05") db 3)

    (let [escalated (op/execute-proposal!
                       (advisor/flag-safety-concern "enrollee-1" "facility-hazard"
                                                     "loose wiring reported near workshop bench 4" "high")
                       db 3)]
      (op/approve-and-commit! (:proposal escalated) db "op-1"))

    (op/execute-proposal! (advisor/propose-facility-booking "enrollee-3" "shop-1" "2026-08-01" 2) db 2)

    (op/execute-proposal!
      (-> (advisor/propose-supply-request "enrollee-2" ["workbooks"] "2026-08-01")
          (advisor/with-rationale "This actually concerns the course curriculum design for the next term"))
      db 3)

    (op/execute-proposal! (advisor/propose-enrollment-scheduling "enrollee-2" "course-2" "2026-08-01") db 0)
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger enrollee-id]
  (last (filter #(= (:enrollee-id %) enrollee-id) ledger)))

(defn- status-cell [ledger enrollee-id]
  (let [f (last-fact-for ledger enrollee-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (case rule
          :enrollee-unverified "<span class=\"critical\">HARD hold &middot; unverified enrollee</span>"
          :scope-excluded "<span class=\"critical\">HARD hold &middot; scope-excluded</span>"
          (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>")))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- enrollee-row [ledger {:keys [enrollee-id name skill-level registered? verified?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc enrollee-id) (esc name) (esc skill-level)
          (if (and registered? verified?) "<span class=\"ok\">registered &amp; verified</span>"
              "<span class=\"warn\">registered, unverified</span>")
          (status-cell ledger enrollee-id)))

(defn- ledger-row [{:keys [t op enrollee-id disposition reason violations]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc (or enrollee-id ""))
          (esc (or reason (some-> disposition name)
                   (when (seq violations) (name (:rule (first violations)))) ""))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract
  ;; (README `Ops` table, `vocationalops.governor`/`vocationalops.phase`) --
  ;; documentation of fixed behavior, not runtime telemetry, so it is
  ;; legitimately hand-described rather than derived from a live run.
  ["        <tr><td><code>:schedule-course-enrollment</code></td><td><span class=\"ok\">phase-3 auto when clean</span></td></tr>"
   "        <tr><td><code>:coordinate-facility-booking</code></td><td><span class=\"ok\">phase-3 auto when clean</span></td></tr>"
   "        <tr><td><code>:coordinate-supply-request</code></td><td><span class=\"ok\">phase-3 auto when clean</span></td></tr>"
   "        <tr><td><code>:schedule-instructor-shift-proposal</code></td><td><span class=\"ok\">phase-3 auto when clean</span></td></tr>"
   "        <tr><td><code>:flag-safety-concern</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto, any phase</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        enrollees (store/all-enrollees db)
        enrollee-rows (str/join "\n" (map (partial enrollee-row ledger) enrollees))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-854 &middot; vocational/continuing-education operations coordination</title><style>\n"
     "table { width: 100%; border-collapse: collapse; font-size: 14px; }\n"
     ".ok { color: #137a3f; }\n"
     "body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }\n"
     "header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }\n"
     "th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }\n"
     "h2 { margin-top: 0; font-size: 15px; }\n"
     ".warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }\n"
     "main { max-width: 980px; margin: 24px auto; padding: 0 20px; }\n"
     "header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }\n"
     ".muted { color: #888; font-size: 13px; }\n"
     ".critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }\n"
     ".card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n"
     ".err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }\n"
     "th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }\n"
     "header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }\n"
     "code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }\n"
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Vocational/continuing education back-office coordination (ISIC 854) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · never touches course-content/curriculum/learner-progress</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Enrollees</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>vocationalops.store</code> via <code>vocationalops.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Enrollee</th><th>Name</th><th>Skill level</th><th>Roster status</th><th>Last coordination status</th></tr></thead>\n"
     "      <tbody>\n"
     enrollee-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (VocationalOps Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Course-content, curriculum, instructional materials, tutor competency/certification, learner progress and safety-authority territory are permanently out of scope — see governor scope-exclusion.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Enrollee</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/coordination-log db)) "committed coordination records )")))
