# Contributing to cloud-itonami-isic-854

## Governance

This project follows the broader cloud-itonami governance framework:
- **License**: AGPL-3.0-or-later
- **Decision Making**: Consensus-driven, with final authority held by the cloud-itonami steering committee
- **Scope**: Administrative coordination only for adult vocational/continuing education providers

## Development Workflow

1. **Fork and branch**: Create a feature branch off `main`
2. **Write tests**: All code changes must include tests in `test/`
3. **Run tests**: Verify all tests pass locally before submitting a PR
4. **Code review**: Submit a pull request for review by maintainers
5. **Merge**: Upon approval, maintainers will merge to `main`

## Code Standards

- All code must be `.cljc` (Clojure/ClojureScript compatible)
- Module structure follows: `store` → `governor` → `phase` → `advisor` → `sim`
- Tests are written in Clojure (`.clj`) and run on the JVM
- Keep the three hard checks in `governor` immutable and add no overrides or exceptions

## The Three Hard Checks (Do Not Modify)

The following checks in `governor.cljc` are **permanent** and **non-negotiable**:
1. **Enrollee Verified**: Re-derives from store, never from self-report
2. **Effect is :propose**: Rejects any non-:propose effect outright
3. **Scope Exclusion**: Blocks curriculum/instruction/certification/assessment/discipline decisions

Any pull request that weakens, overrides, or adds exceptions to these checks will be rejected.

## Scope Boundaries (Do Not Expand)

This actor handles **administrative coordination only**. The following categories are permanently out of scope:
- Course content & curriculum
- Instructional materials
- Instructor competency & certification
- Learner progress & assessment
- Learner discipline
- Safety-authority overrides

Pull requests that propose new operations or expand scope to touch these areas will be rejected.

## Reporting Issues

- **Bug reports**: Use GitHub Issues with a clear title and reproduction steps
- **Security issues**: Email the maintainers directly; do not open a public issue
- **Feature requests**: Must align with the actor's administrative coordination scope

## Contact

Questions? Contact the cloud-itonami team or file an issue on GitHub.
