# Tasks: Android Auto

Feature: `004-android-auto` · One PR, one commit per group, tests first.

- [x] T001 Group 0: spec, plan, tasks; amend REQ-PROVIDER-002 (spec 001), REQ-REFRESH-002 and
  REQ-SURF-005 (spec 002).
- [x] T002 Group 1: the counted surface timer (`PeriodicRefreshTest`), the process-wide controller.
- [x] T003 Group 2: `CarStatusScreenTest` first, then the car screen, the service, the session, the
  manifest and the car-app 1.7.0 dependency.
- [x] T004 Group 3: the onboarding and listing copy name Android Auto again; the copy guard follows
  the manifest; specs 002 T018 and 005 updated.
- [x] T005 PR with the review packet.
- [x] T007 Review fixes: the template quota (H1), stale reads No Current Data (M1), the CarPlay
  refresh cycle (M2), session wiring tests (M3), the review-risk note (M4); lows L1, L2, L5, L6; re-review lows: the supersession test, the cycle ends on disconnect, one Checking, spec 002 wording.
- [ ] T006 Desktop Head Unit and real-car runs (SC-003), when a device is available.

## Backlog from the review

- L3: on a fresh install the car shows Kyiv as if resolved; it has no "no region yet" state.
- L4: with the phone and the car both started, each update redraws the widget twice.
- Owner decision: stale data still lists nearby regions, on the car and on the phone
  (`StatusPresenter` computes them from any snapshot). iOS drops the nearby sentence from the
  summary while stale but keeps the row; whether the car and the phone should hide stale nearby
  names is the owner's call.
