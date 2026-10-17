# Feature specification: Android Auto

Feature: `004-android-auto` · Created: 2026-10-17
Status: not in 1.0 (owner, 2026-10-17, through the orchestrator: «А»)
Input: the iOS CarPlay screen (`regional-check` REQ-SURF-005, REQ-SURF-006)

## Decision

Android Auto is not part of 1.0. The phone app, the widget, the quick-settings tile and the alert
notification carry the signal. No string and no listing text promises a car screen; two checks
enforce this:
- `ReleaseCopyGuardTest`;
- `scripts/listing-lengths.py`.

## Why

Google Play lists an app for Android Auto only in a supported category. None of them fits an
air-raid alert app. Read on developer.android.com, 2026-10-17:

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

Declaring the weather category (`androidx.car.app.category.WEATHER`, Car App Library 1.7.0)
would very likely fail that review. The failed review would also hold back the closed-test build
and its updates.

## Reopening

The owner reopens this spec if Google Play confirms that a public-safety alert app is accepted in
a car category. The iOS CarPlay screen (REQ-SURF-006) is then the model: one screen with the
status, the region and its update time, nearby alerts only when there are any, and Refresh.
