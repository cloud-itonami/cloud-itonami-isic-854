# Security Policy

## Reporting Security Issues

**Do not open a public GitHub issue for security vulnerabilities.**

If you discover a security issue, please email the maintainers directly at the contact provided in the cloud-itonami organization.

Please include:
- A clear description of the vulnerability
- Steps to reproduce (if possible)
- The impact or severity of the issue
- Your suggested fix (if you have one)

We will acknowledge receipt within 48 hours and provide a timeline for addressing the issue.

## Security Considerations

### Hard Governance Checks

This actor's security model relies on three permanent, un-overridable checks in the Governor:

1. **Enrollee Verification**: Target enrollee must be verified in the store before any proposal can commit or escalate. This prevents unauthorized coordination on behalf of non-registered parties.
2. **Effect is :propose**: Proposals always claim `:propose` (never execute directly). This ensures all coordination is auditable and reversible.
3. **Scope Exclusion**: Proposals touching curriculum, instruction, assessment, or discipline are hard-blocked. This prevents the actor from being weaponized for unauthorized educational decisions.

These checks are **permanent** and cannot be overridden by human approval, configuration, or rollout phases.

### Audit Trail

All proposals, decisions, and outcomes are logged immutably in the coordination ledger:
- Proposal ID, timestamp, enrollee, operation type, confidence
- Governor decision (HOLD/ESCALATE/COMMIT)
- Any violations encountered
- Human approvals (if applicable)

This ledger enables forensic analysis and reconstruction of decision history.

### Scope Enforcement

This actor is narrowly scoped to administrative coordination and explicitly excludes:
- Educational decisions (curriculum, pedagogy, instruction)
- Competency or qualification determinations
- Learner assessment or progress evaluation
- Disciplinary decisions
- Safety-authority overrides

Attempting to use the actor for any of these purposes will be hard-blocked and logged.

## Testing & Verification

Before deployment:
- All tests must pass: `clj -M:test`
- Governor checks must be verified to reject scope-excluded proposals
- Audit ledger must demonstrate immutable logging of all decisions

## Compliance

This project is AGPL-3.0-or-later licensed. Users who modify the code and deploy it must make their modifications available under the same license.

## Updates & Patches

Security patches will be released as new versions. Users are encouraged to monitor releases and update promptly.
