# Feature specification: Android Auto

Feature: `004-android-auto` · Created: 2026-10-17
Status: approved (owner, 2026-10-17, through the orchestrator: «Android Auto доделать. Это мастхев функционал»; this replaces his earlier «А», no Android Auto in 1.0)
Input: the iOS CarPlay screen (`regional-check` REQ-SURF-005, REQ-SURF-006)

## Summary

The free signal reaches the car screen as on iOS CarPlay. Android Auto gets one template screen:
- the status in the title;
- the region with its update time;
- nearby alerts, only when there are any;
- a location row, only when access is denied;
- Refresh as the only action.

It is built with the Android for Cars App Library (`androidx.car.app`, pinned to the stable 1.7.0).

## The category, and the accepted risk

Google Play lists a car app only in a supported category. Read on developer.android.com, 2026-10-17:

- `training/cars/apps`: "Your app must belong to one of the supported categories and meet
  specific design requirements before it can be listed on Google Play for Android Auto and
  Android Automotive OS."
- `docs/quality-guidelines/car-app-quality`:
  - The categories are Media, Communication (messaging, calling), Navigation, Point of Interest,
    Internet of Things, Weather, Video, Games and Browsers. None of them is for alerts or safety.
  - WE-1: "App must include weather related content, which must be relevant to the user's current
    location or a user specified location."
  - PC-1: "The app must not include features outside the app types intended for cars."
  - Car apps are reviewed manually, and updates are not distributed until they are approved.
The app declares `androidx.car.app.category.WEATHER`, the only listed category whose content is
about the driver's current location. An air-raid status is not weather content, so a rejection
under WE-1 or PC-1 is the likely outcome of the car review. The owner accepts that risk. A
rejection would hold back the Play release until the car service is removed or an appeal succeeds.

## Sideloaded builds

Android Auto's "Allow unknown sources" developer option "doesn't apply to apps built using the
Android for Cars App Library" (`training/cars/testing`). An APK installed from GitHub therefore
runs its phone screens, widget, tile and notification, but shows no car screen in a real car.

Outside Google Play, the car screen runs only in the Desktop Head Unit. Debug builds accept any
host for this; release builds accept only the hosts in Google's allowlist.

## Requirements: amended in earlier specs

- REQ-PROVIDER-002 (spec 001) gains two triggers: the car screen starting, and its Refresh.
- REQ-REFRESH-002 (spec 002): the phone and the car share one process-wide controller, and its
  one timer runs while either is started.
- REQ-SURF-005 (spec 002): the car shows the nearby row only when there is one.

## Requirements: new in this spec

### REQ-SURF-006 — One free car screen

Status: approved

Core: P1, P2

Source: iOS REQ-SURF-006 (amended 2026-09-21), `docs/requirements/surfaces-and-pro-gating.md`

Given an Android Auto session\
When its screen is built\
Then it is a single pane with no tabs and no map, and no status change ever counts as a new
template. Its contents are:
- the app's name as the fixed title;
- a "Status" row: the status in the full form, then the region with its update time, with the
  status icon (red alert, yellow alert, green check);
- a "Nearby" row: the names under alert, or "No alerts nearby"; then the location line, only when
  access is denied;
- Refresh as the only action.

It redraws when the status changes. The app is declared in the weather car category.

Differs from iOS:
- This slice does not track location in the car. The car shows the region the phone last resolved
  and saved.
- The status is not the title, and the nearby row is always there. `PaneTemplate` counts a change
  of the title, the row count or a row title as a new template, and the host closes an app after
  five of them. A drive during an attack would reach that limit, so the title and the row titles
  stay fixed and the changing words are row texts. The content is the iOS screen's.

### REQ-REFRESH-004 — Car refresh cycle

Status: approved

Core: P1

Source: iOS REQ-REFRESH-004, `docs/requirements/refresh-policy.md`

Given the car connects, or the driver taps Refresh on the car screen\
When fetches fail\
Then the cycle makes up to 3 attempts with 2 s then 4 s backoff, stops at the first answer and
while rate limited, and a new cycle supersedes the old one without cancelling the request shared
with the phone

Differs from iOS: an attempt that the 2 s gap between requests (REQ-ANDROID-001) holds waits for
the gap and is not counted.

### REQ-REFRESH-007 — Car freshness by age

Status: approved

Core: P1, P2

Source: iOS REQ-REFRESH-007, `docs/requirements/refresh-policy.md`

Given the car screen shows a cached status\
When it is stale\
Then it reads "No Current Data" with its update time and without a status icon, an alarm included;
a fresh status keeps its word and icon

## Success criteria

- **SC-001:** the gate and the trace are green.
- **SC-002:** `CarStatusScreenTest` covers the pane's rows, the action, the template quota, the
  stale state, the redraw, the manifest category and the session's cycle. `CarRefreshCycleTest`
  covers the cycle; `PeriodicRefreshTest` covers the counted timer.
- **SC-003:** a Desktop Head Unit run and a real car run, once a device is available.
