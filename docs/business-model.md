# Business Model: Other Education — Adult Vocational & Continuing Education (Administrative Coordination)

## Classification
- Repository: `cloud-itonami-isic-854`
- ISIC Rev.4: `854` — other education (adult vocational/continuing
  education), narrowed to back-office administrative COORDINATION (never
  course-content decisions, curriculum, tutor-competency/certification, or
  learner-progress/completion decisions)
- Social impact: learner-safety administrative-coordination equitable-access

## Customer

- adult vocational/continuing-education providers (trade schools, corporate
  upskilling programs, community continuing-ed departments) who need
  enrollment scheduling, facility/equipment booking and instructor shift
  coordination without buying a full LMS/course-content platform they don't
  need
- small training providers priced out of enterprise LMS licensing

## Problem

Course enrollment scheduling, facility/equipment booking, supply
coordination, instructor shift proposals and safety-concern flagging
(facility hazards, participant wellbeing) run on paper or spreadsheets, with
no immutable record and no structural guarantee a safety concern reaches a
human. The nearest commercial comparables are LMS/course-authoring
platforms (TalentLMS, Coursebox, Trainual) — a genuinely different product
category (course CONTENT delivery, not back-office coordination) that a
provider would still need in ADDITION to something like this actor, not
instead of it.

## Offer

- `schedule-course-enrollment`, `coordinate-facility-booking`,
  `coordinate-supply-request`, `schedule-instructor-shift-proposal`,
  `flag-safety-concern` (facility hazards, participant wellbeing; always
  escalates, never auto-commits at any phase)
- an independent Governor with permanent scope-exclusion on course-content
  decisions, curriculum, tutor-competency/certification and
  learner-progress/completion decisions
- an append-only audit ledger

## Funnel (demo → fork → certified operator)

1. **Demo** — the nightly build-time-regenerated operator console.
2. **Fork / self-host** — AGPL-3.0-or-later; run the actor for one provider.
3. **itonami.cloud certification** (optional) — same trust ladder as every
   cloud-itonami venture.

## Revenue

| Package | Customer | Price shape (example) |
|---|---|---|
| Self-host starter | training-provider IT lead | setup ¥100k–250k + optional support |
| Managed Training Ops (Starter) | one provider, unlimited staff seats | ¥20,000/月 flat |

**Market-anchored (2026-07-23)**: no exact "back-office coordination for a
training provider" comparable exists publicly, so this is anchored against
the nearest real comparables — LMS/course-content platforms a training
provider already budgets for, even though they solve a DIFFERENT problem
(course content/delivery, not scheduling/facility/staffing coordination):
**TalentLMS** Core $149/mo flat for up to 100 users (annual: ~$119/mo);
**Trainual** $3–5/user/mo (no longer publishes self-serve pricing, sales
process now); **Coursebox** Creator $30/mo, Pro $300/mo, Business $700/mo.
This spread ($30–700/mo depending on tier) reflects real willingness-to-pay
for *some* ed-tech software at a small training provider, even though none
of these tools do what this actor does. ¥20,000/月 (~$130/mo at ~¥150/$)
sits inside that range, at the lower-middle, reflecting that this is a
narrower, single-purpose coordination tool rather than a full LMS — and
**deliberately NOT** the ¥50,000–150,000/月 portfolio-uniform range used by
the HR/CRM/recruiting flagships, which this market's own (admittedly
imperfect) comps don't support either.

**Subscribe (2026-07-23)**: a live Stripe Payment Link for the Managed
Training Ops (Starter) tier (¥20,000/月 flat) is available now —
[**subscribe to Managed Training Ops — Starter**](https://buy.stripe.com/8x24gyewP8FOeWYdIzbMQ0o).
This is a no-code Stripe-hosted checkout; nothing in this repo's actor code
changed. After subscribing, contact gftdcojp via an [operator-interest
issue](https://github.com/cloud-itonami/cloud-itonami-isic-854/issues/new?template=operator-interest.yml)
to arrange managed-tenant setup (manual fulfillment today, no automated
onboarding yet). **No provider has claimed or subscribed to this tier
yet — this is a live, working checkout with zero paid tenants, not a
claim of existing revenue.**

## Unit Economics (worked example, illustrative)

Same shape as `cloud-itonami-isic-851`: infrastructure ≈ ¥3k–8k/月, LLM cost
bounded to proposal-time only, human approval labor is the real cost driver
(~2–4 h/月 once the rollout phase stabilizes), support ~2–3 h/月. Business
scales with number of providers per operator, not proposal volume.

## Open Participation

Anyone may fork, run the demo, self-host, submit patches, and build a local
operator business. itonami.cloud certification is required before an
operator is listed, receives leads, or runs managed tenants under the
platform brand.

## Operator Trust Levels

| Level | Capability |
|---|---|
| Contributor | patches, docs, examples |
| Self-host operator | runs their own provider's instance, no platform endorsement |
| Certified operator | listed on itonami.cloud after review |
| Managed operator | may receive leads and operate customer tenants |
| Core maintainer | can approve changes to governor, security and governance |

## Trust Controls

- a proposal for an unregistered/unverified enrollee is never committed or
  even escalated
- any proposal whose effect is not `:propose`, or whose content touches
  course-content/curriculum/tutor-competency/learner-progress territory, is
  permanently blocked
- a safety-concern flag always reaches a human; it can never auto-commit
- every commit, hold, escalation and approval path is auditable
- sensitive learner data stays outside Git

## Non-Negotiables

- Do not commit real learner PII to this repository.
- Do not bypass the Governor for production commits.
- Do not expand the proposal-op allowlist to course-content, curriculum,
  tutor-competency/certification, or learner-progress/completion decisions
  without a new ADR.
- Do not market an uncertified deployment as an itonami.cloud certified
  operator.
