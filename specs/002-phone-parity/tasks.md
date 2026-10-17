# Tasks: phone parity

Feature: `002-phone-parity` · Spec: [spec.md](spec.md) · Plan: [plan.md](plan.md) · Created: 2026-10-17

One pull request; one commit per group, in order. In each group the tests come first and are seen
failing. After each group that changes the UI, the owner gets the Roborazzi screenshots.

## Group 0 — spec

- [x] T001 Write spec, plan and tasks; amend spec 001 (REQ-PROVIDER-002, REQ-REFRESH-006, REQ-SURF-001).

## Group 1 — screenshot tests

- [ ] T002 Add Roborazzi, Robolectric and the Compose test rule (test scope only). Record the
  current Status screen in No Alert, Air Raid Alert, Unavailable and Checking. Wire
  `verifyRoborazziDebug` into `check`, and prove that a changed colour fails it.

## Group 2 — persistence, periodic refresh, staleness

- [ ] T003 Tests: `REQ_ANDROID_006_*` (store round-trip, corrupt store reads empty),
  `REQ_REFRESH_002_*` (60/30/300 s, power saver wins over alarm, jitter bounds, a scheduled poll is
  skipped in the 429 window, one loop), `REQ_REFRESH_006_*` (stale after 2 × base interval, stale
  alarm stays alarm).
- [ ] T004 Implement `StatusStore`, `PreferencesStatusStore`, `RefreshPolicy` intervals,
  `PeriodicRefresh`, the stale presentation and `AndroidRefreshEnvironment`.

## Group 3 — nearby alerts and Stay Alert

- [ ] T005 Tests: `REQ_SURF_005_*` (neighbour table, line with two names and "+N more", no line
  without neighbours), `REQ_SURF_010_*` (half the neighbours, half the country, the Kyiv Oblast
  rule, one-way, never on stale data).
- [ ] T006 Implement `NearbyRegionPolicy` and the presentation; the hero and nearby line in the UI;
  screenshots.

## Group 4 — region model

- [ ] T007 Tests: `REQ_REGION_004_*` (resolver forms), `REQ_REGION_005_*` (accuracy, age,
  throttle, retry after the wait, 15 s bound), `REQ_REGION_006_*` (hysteresis, first resolve
  commits), `REQ_REGION_008_*` (outside keeps the last region, sheet once).
- [ ] T008 Implement `AlertRegionResolver` and `RegionTracker`; wire the region into the controller.

## Group 5 — location on Android, onboarding, notices

- [ ] T009 Tests: `REQ_REGION_010_*` (no location before Get Started), `REQ_REGION_009_*` (denied
  keeps the last region and shows the card), `REQ_REGION_007_*` (notice, dismissible, no Undo).
  Screenshots: onboarding, outside-Ukraine sheet, notice, denied card.
- [ ] T010 Implement the platform location source, the geocoder, the permission flow, onboarding,
  the sheet and the notice.

## Group 6 — tabs, map, region list

- [ ] T011 Tests: `REQ_ANDROID_005_*`, `REQ_ANDROID_002_*` (once per session, 3 s after settle,
  failure state, caption, a new session reloads), `REQ_ANDROID_003_*` (sections, sort, pills),
  `REQ_SURF_012_*`, `REQ_SURF_013_*` (compact hero, narrowed map, scroll). Screenshots.
- [ ] T012 Implement the shell, `MapController`, the map card, the region list and the layout rules.

## Group 7 — Details

- [ ] T013 Tests: `REQ_ANDROID_004_*` (summary text in three languages, row order, denied row,
  version). Screenshots.
- [ ] T014 Implement `CountrySummary` and `DetailsScreen`.

## Group 8 — launch and haptics

- [ ] T015 Tests: `REQ_LAUNCH_001_*` to `REQ_LAUNCH_005_*`, `REQ_REFRESH_011_*` (confirm, reject,
  none for automatic refreshes).
- [ ] T016 Implement the launch theme, the cached-status start, the motion and TalkBack rules, and
  the haptics.

## Pull request

- [ ] T017 Push, open the PR with the review packet and the screenshots; emulator runtime checks
  on a quiet machine; the owner approves the merge.
