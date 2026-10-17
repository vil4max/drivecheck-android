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
- IN-1 names "imminent alerts for the current location" as relevant content for a weather app.

The app declares `androidx.car.app.category.WEATHER`. That is the only listed category whose
content is a status at the driver's current location. The owner accepts the risk that the car
review rejects it as not weather content. A rejection would hold back the Play release until the
car service is removed or an appeal succeeds.

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
Then it is a single pane with no tabs and no map. Its contents are:
- the status in the title, in the full form;
- the region with its update time ("Last known: … · HH:mm" once stale);
- the nearby row, only when a neighbouring region is under alert (REQ-SURF-005);
- the location-denied row, only when access is denied;
- Refresh as the only action.

It redraws when the status changes. The app is declared in the weather car category.

Differs from iOS:
- This slice does not track location in the car. The car shows the region the phone last resolved
  and saved.
- The pane is `PaneTemplate`. Its glyph is the host's alert icon, red in alarm and yellow for Stay
  Alert.

## Success criteria

- **SC-001:** the gate and the trace are green.
- **SC-002:** `CarStatusScreenTest` covers the pane's rows, the action, the redraw and the
  manifest category. `PeriodicRefreshTest` covers the counted timer.
- **SC-003:** a Desktop Head Unit run and a real car run, once a device is available.
