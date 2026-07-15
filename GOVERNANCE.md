# Project Governance

## Overview

This project is part of the [cloud-itonami](https://github.com/cloud-itonami) fleet and follows the governance framework established in the parent organization.

## Decision-Making

- **Steering Committee**: Final authority on all architectural and scope decisions
- **Consensus-Driven**: All contributors are encouraged to propose ideas and engage in discussions
- **Transparent**: Decisions are documented in ADRs (Architecture Decision Records) in the superproject

## Scope & Boundaries

This actor is scoped to **administrative coordination only** for ISIC class 854 (other education, adult vocational/continuing education).

### In Scope
- Course enrollment/registration scheduling logistics
- Facility and equipment booking coordination
- Supply ordering and logistics (non-instructional consumables)
- Instructor shift proposals (administrative coordination, never binding)
- Safety-concern flagging and escalation

### Permanently Out of Scope
- Course content and curriculum decisions
- Instructional-materials selection
- Tutor/instructor qualification, competency, or certification determinations
- Learner progress, performance, or completion decisions
- Learner disciplinary action or enrollment termination
- Safety-authority overrides (CPS liaison, law-enforcement coordination)

**Note**: Scope boundaries are structural and not subject to rollout phases. No phase upgrade will ever permit operations in out-of-scope categories.

## Hard Governance Checks

Three governance checks in `governor.cljc` are **permanent, un-overridable, and non-negotiable**:

1. **Enrollee Verification**: Every proposal's target enrollee must be independently verified `:registered?`/`:verified?` in the store. Never relies on self-report.
2. **Effect is :propose**: Every proposal's `:effect` must be `:propose`. Claims to execute or commit outside governance are HARD-blocked.
3. **Scope Exclusion**: Any proposal touching scope-excluded territory is HARD-blocked, regardless of confidence or human approval.

These checks exist to ensure the actor remains a **trusted, auditable coordination service** and not a tool for unauthorized decision-making.

## Contributing & Approval

- Pull requests are reviewed by maintainers and must align with scope boundaries
- Any proposal to weaken or override the three hard checks will be rejected
- Any proposal to expand scope to out-of-scope categories will be rejected
- All governance changes require discussion and consensus

## References

- **ADR-2607153800**: This project's decision record and scope definition
- **ADR-2607121000**: Cloud-itonami Wave structure and rollout plan
- **ADR-2607051621**: Murakumo AGPL governance model
