# Feature specification: widget, quick-settings tile, ongoing alert notification

Feature: `003-widgets-tile-notification` · Created: 2026-10-17
Status: approved (owner, 2026-10-17, through the orchestrator: «заканчивай проект и в маркет», no spec gate; merge needs his word)
Input: the iOS Status widget, the Control Center control and the Live Activity
(`regional-check` `docs/requirements/refresh-policy.md`, `surfaces-and-pro-gating.md`)

## Summary

The free signal is visible outside the app, as on iOS:
- **Home-screen widget:** shows the status with its honest age, including Stay Alert, and has a Refresh button.
- **Quick-settings tile:** shows the status and opens the app.
- **Ongoing notification:** the Live Activity counterpart. It appears when the current region is in alarm and ends on a confirmed all-clear.

All three read the snapshot the app keeps (REQ-ANDROID-006). The widget and the notification's Refresh fetch once, under the same polite-load rules.

No new library: the widget is a classic `AppWidgetProvider` with `RemoteViews`, and the notification and tile are platform APIs.

## Requirements: amended in earlier specs

REQ-PROVIDER-002 (spec 001) gains three triggers:
- the widget's periodic update, every 30 min or longer, at the system's own schedule;
- the widget's Refresh button;
- the notification's Refresh action.

Each is one request with its one transient retry. A scheduled widget update honours the stored rate-limit window (REQ-REFRESH-005).

REQ-SURF-010 (spec 002) extends to the widget and the tile: they show "Stay Alert" in yellow when the current region is surrounded on fresh data. A stale widget is grey, never yellow (iOS amendments of 2026-09-22 and 2026-09-23).

## Requirements: new in this spec

### REQ-REFRESH-008 — Widget reload schedule

Status: approved

Core: P2

Source: iOS REQ-REFRESH-008

Given a widget is on the home screen\
When the system runs its periodic update\
Then the widget fetches once (skipped inside a stored rate-limit window), saves a successful snapshot for the app, and a failed fetch keeps the last known good snapshot

Differs from iOS: Android updates a widget at most every 30 minutes (`updatePeriodMillis`), where iOS asks for 180/300/120 s within WidgetKit's budget. The freshness tiers below make the age visible.

### REQ-REFRESH-009 — Widget freshness tiers

Status: approved

Core: P2

Source: iOS REQ-REFRESH-009

Given a widget shows a snapshot\
When its age crosses 3 min or 10 min\
Then it marks the time with ⚠, keeps the real status visible, turns a quiet status light grey, keeps a known alarm red, and is never replaced by a connection-error screen

### REQ-SURF-003 — Honest age on widgets and the notification

Status: approved

Core: P2

Source: iOS REQ-SURF-003 (amended 2026-09-23)

Given a widget or the ongoing notification shows aging data\
When the data ages\
Then the widget shows the time with ⚠, and from 10 min "Open the app to update"; the notification of a stale alarm stays red and says "May be outdated. Open the app."

### REQ-SURF-009 — The ongoing notification follows the alert

Status: approved

Core: P1, P2

Source: iOS REQ-SURF-009 (Live Activity)

Given the in-app switch and the system both allow the notification (REQ-SURF-008)\
When the app or a widget update sees the current region in alarm\
Then an ongoing notification shows the region, "Air Raid Alert" and the update time, and stays when the app goes to the background. It ends as soon as a confirmed all-clear is seen. A failed, stale or unknown refresh never ends it. While there is no alarm, none is posted. Its Refresh action fetches once (8 s) and applies the result like any refresh, and never posts a notification.

Differs from iOS: the Live Activity is an Android ongoing notification. Since Android 14 the driver can swipe it away; it comes back with the next alarm update. The periodic widget update also refreshes it while the app is closed, which iOS cannot do.

### REQ-SURF-008 — The switch never promises what Android refuses

Status: approved

Core: P2

Source: iOS REQ-SURF-008

Given the Details tab shows the "Notification during an alert" switch\
When Android blocks the app's notifications (the permission on Android 13+, or the app or channel turned off)\
Then the switch reads off and cannot be turned on, a line says notifications are off in Android settings and offers Open Settings, and the driver's own choice is kept for when Android allows them again. Turning the switch on asks for the permission when Android needs it.

### REQ-ANDROID-007 — Quick-settings tile

Status: approved

Core: P1, P2

Source: iOS behaviour without an ID: the Control Center control (`surfaces-and-pro-gating.md` matrix, REQ-SURF-010 amendment)

Given the tile is in quick settings\
When it is shown\
Then it reads the stored snapshot. The label is the short status ("No Alert", "Alert", "Stay Alert", or "No Current Data" when stale or missing) and the subtitle is the region. The tile is active in alarm and Stay Alert, inactive when quiet, and unavailable without data. A tap opens the app.

Differs from iOS: Android draws the tile's colours itself, so the traffic light is carried by the active and inactive states and the label.

## Success criteria

- **SC-001:** the gate and the trace are green, with all approved IDs of specs 001 to 003.
- **SC-002:** Roborazzi screenshots of the widget (fresh, aging and expired, quiet, Stay Alert and alarm) and of the Details notification switch in its three states.
- **SC-003:** device runtime checks once a device or a quiet machine is available.
