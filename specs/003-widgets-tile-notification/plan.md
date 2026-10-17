# Implementation plan: widget, tile, notification

Feature: `003-widgets-tile-notification` · Spec: [spec.md](spec.md) · Tasks: [tasks.md](tasks.md)

## Design

- **`shared/commonMain`:**
  - `surface/WidgetPresenter`: tier, phase (with Stay Alert) and the age line from a stored snapshot and the current time (REQ-REFRESH-009, REQ-SURF-003, REQ-SURF-010).
  - `surface/AlertNotificationPolicy`: post, update or end from the region's status and whether the answer is confirmed (REQ-SURF-009).
  - `surface/TilePresenter`: label, state and subtitle (REQ-ANDROID-007).
  - `surface/BackgroundRefresh`: one fetch for a widget update or a notification action. It honours the stored rate-limit window for scheduled updates, saves a success and keeps the last good snapshot on failure (REQ-REFRESH-008).
- **`androidApp`:**
  - `StatusWidgetProvider` (`AppWidgetProvider`, `goAsync` with an 8 s bound), RemoteViews layouts, and a Refresh `PendingIntent`.
  - `AlertNotifier`: a channel, an ongoing notification and a Refresh action through a `BroadcastReceiver`.
  - `StatusTileService`.
  - The Details switch, and `POST_NOTIFICATIONS` on Android 13+.
  - After every app refresh, the widget, the tile and the notification are updated from the same snapshot.

## Tests

- Common unit tests for the presenters and the policy.
- Robolectric tests:
  - the widget `RemoteViews` applied to a view and captured with Roborazzi;
  - the receiver and provider wiring;
  - the Details switch states.
