# cloud-itonami-isic-854

Other Education (Adult Vocational & Continuing Education) Coordination Actor

## Overview

This repository implements the `vocationalops` actor for ISIC Rev.4 class 854 (other education) in the [cloud-itonami](https://github.com/cloud-itonami) fleet. The actor coordinates administrative operations for adult vocational and continuing education providers (driving schools, language schools, exam-prep centers, trade/skill courses).

**Scope**: Administrative coordination only — course enrollment scheduling, facility booking, supply logistics, instructor shift proposals, and safety-concern flagging. Never touches course content, curriculum, instructor competency/certification, learner assessment, or disciplinary decisions.

## Architecture

```
┌──────────────┐
│   Advisor    │  LLM/reasoning layer proposes operations
└──────┬───────┘
       │ :propose (always)
       ▼
┌──────────────────────────────┐
│      Governor (3 Checks)     │  Hard compliance layer
│ 1. Enrollee verified?        │  • Re-derived from store, never self-report
│ 2. Effect is :propose?       │  • Rejects non-:propose effects outright
│ 3. Scope exclusion?          │  • Blocks curriculum/instruction/certification decisions
└──────┬───────────────────────┘
       │ HOLD / ESCALATE / COMMIT
       ▼
┌──────────────────┐
│      Phase       │  Rollout phases (0-3) control what auto-commits
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│    Ledger        │  Append-only audit trail (immutable)
└──────────────────┘
```

### Modules

- **`store.cljc`**: SSoT (Single Source of Truth) for enrollee directory, ledger, coordination log. Protocol-based for extensibility.
- **`governor.cljc`**: Independent compliance layer with three permanent hard checks. Rejects violations outright.
- **`phase.cljc`**: Rollout phases (0: read-only; 1: enrollment; 2: facility/supply; 3: auto-commit). Controls what operations are available and how they're handled.
- **`advisor.cljc`**: LLM/reasoning layer. Proposes operations with confidence & rationale, never executes.
- **`operation.cljc`**: Closed allowlist of five allowed operations (enrollment, facility, supply, shift, safety).
- **`sim.cljc`**: Simulation harness. Runs proposals through the full pipeline for testing & demo.

## Allowed Operations (Closed Allowlist)

1. `:schedule-course-enrollment` — Register enrollee for a course/session
2. `:coordinate-facility-booking` — Book classroom/training room
3. `:coordinate-supply-request` — Non-instructional consumables (office/training-room supplies)
4. `:schedule-instructor-shift-proposal` — Admin shift coordination (proposal only, never binding)
5. `:flag-safety-concern` — Facility/wellbeing safety concerns (ALWAYS escalates)

## Scope Exclusions (Permanent, Un-Negotiable)

**This actor NEVER handles:**
- Course content or curriculum decisions
- Instructional-materials selection
- Tutor/instructor competency, qualification, or certification determinations
- Learner progress, assessment, or completion decisions
- Learner disciplinary action or enrollment termination
- Safety-authority overrides (mandatory reporting, law-enforcement liaison)

## Three Hard Checks (Permanent, Un-Overridable)

1. **Enrollee Verified**: Target enrollee must exist AND be `:registered?`/`:verified?` in the store. Re-derived from store record, never from proposal self-report.
2. **Effect is :propose**: Any `:effect` value other than `:propose` is rejected outright. This actor proposes only; it does not execute.
3. **Scope Exclusion**: Any proposal touching scope-excluded territory (curriculum, instruction, certification, learner assessment, discipline, safety-authority) is HARD-blocked regardless of confidence or how clean other checks are.

## Usage

### Running the Demo

```bash
clj -A:nbb -c "vocationalops.sim" -e "(sim/demo-run)"
```

### Running Tests

```bash
clj -M:test
```

## Rollout Phases

| Phase | Name | Description | Auto-Commits |
|-------|------|-------------|--------------|
| 0 | Read-Only | All proposals held for review | None |
| 1 | Enrollment Scheduling | Course enrollment (approval-gated) | None |
| 2 | Facility & Supply | Add facility booking, supply, shift proposals (approval-gated) | None |
| 3 | High-Confidence Auto-Commit | Auto-commit clean proposals (safety concerns always escalate) | enrollment, facility, supply, shift |

## License

AGPL-3.0-or-later

## References

- **ADR-2607153800**: This actor's decision record (context, scope, governance).
- **ADR-2607153700**: ISIC-852 (secondary education) — direct lineage.
- **ADR-2607072915**: ISIC-851 (primary education) — pattern reference.
- **ADR-2607121000**: Cloud-itonami Wave structure & rollout plan.
- **Registry**: [kotoba-lang/industry](https://github.com/kotoba-lang/industry) entry `854`.
