# drivecheck-android

Repository visibility: **PUBLIC**. Nothing private goes into files or history.

The Android version of Drive Check (DriveCheckUA). The product rules come from the iOS app,
`regional-check` (`docs/core.md`, `docs/requirements/`): one current region, one state, one
data provider (Ubilling aerial alerts), one user action (Refresh); no account, no ads, no
third-party analytics. The iOS app is not changed from here.

## Shape

- `shared/`: a Kotlin Multiplatform module. `commonMain` holds the domain, the provider client
  and the refresh rules; `commonTest` tests them with `kotlin.test`. Its iOS targets are
  compiled in CI only, as proof that the module is multiplatform.
- `androidApp/`: Jetpack Compose, one `ViewModel` exposing one `StateFlow` state.
- Libraries and versions come from the official KMP project template when the skeleton is made.

## How work runs

- Specs live in `specs/<NNN-slug>/` (`spec.md`, `plan.md`, `tasks.md`, GitHub Spec Kit layout).
  Behaviour shared with iOS keeps the iOS `REQ-` id; an Android-only rule gets a new id. Every
  approved requirement is cited by a test name, and CI checks it.
- Each task: a failing test first, then the code.
- The owner's gates: start (spec and task list approved together) and release.
- One pull request per feature, commits by task group, with a review packet in the
  description: each group's task, its diff, the tests proving it, review findings and their
  outcome, runtime evidence, risk. Merge only after the owner approves.
- Gate: `./gradlew check` plus the iOS-target compile of `shared`; CI runs the same commands.
- Release channel: Google Play. A closed test with at least 12 testers for 14 days precedes
  production access, so it starts with the first usable build.
