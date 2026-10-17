# Tasks: widget, tile, notification

Feature: `003-widgets-tile-notification` · One PR, one commit per group, tests first.

- [x] T001 Group 0: spec, plan, tasks; amend REQ-PROVIDER-002 (spec 001) and REQ-SURF-010 (spec 002).
- [x] T002 Group 1 tests: `REQ_REFRESH_009_*`, `REQ_SURF_003_*`, `REQ_SURF_010_*` (widget), `REQ_ANDROID_007_*`, `REQ_SURF_009_*`, `REQ_REFRESH_008_*`.
- [x] T003 Group 1: the shared presenters, the notification policy and the background refresh.
- [x] T004 Group 2 tests: widget RemoteViews screenshots and the update and Refresh wiring.
- [x] T005 Group 2: the widget provider, layouts and strings (en, uk, ru).
- [x] T006 Group 3 tests: notification post, update and end, the Refresh action, the Details switch (`REQ_SURF_008_*`), and the tile.
- [x] T007 Group 3: `AlertNotifier`, the receiver, the tile service, the Details switch, and the permission.
- [x] T008 PR with the review packet; screenshots to the owner.
- [x] T009 Review fixes (H1, M1–M4) with failing tests first; cheap lows L4 and L6.

## Backlog from the review

- L1: an aging quiet widget reads "No Current Data", where the iOS widget shows "Last known" above "No Alert".
- L2: the tile keeps an old alarm active with no age shown, and `STATE_UNAVAILABLE` takes no tap, so a tap
  before the first launch does nothing.
- L3: on Android 14 a swiped notification comes back on every refresh; remember the dismissal through
  `deleteIntent` until the next alarm.
- L5: `Surfaces.updateAll` makes binder calls on the main thread when the app triggers it.
- L7: the widget's and the notification's Refresh are not single-flight; two taps can send two requests.
