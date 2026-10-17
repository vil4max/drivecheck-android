# Feature specification: phone parity

Feature: `002-phone-parity` · Created: 2026-10-17
Status: approved (owner, 2026-10-17: «Я прошу сделать полный дубликат на андроид», with no spec gate; merge still needs his word)
Input: every phone feature of the iOS app (`regional-check` `docs/core.md`, `docs/requirements/`),
except the car screen, widgets, the quick-settings control, the ongoing notification, Siri and Pro, which are later slices

## Summary

The Android app becomes the iOS phone app:
- two tabs, Status and Details;
- the region follows the location, Kyiv without one, with the outside-Ukraine rule;
- adaptive periodic refresh and honest staleness;
- nearby alerts and the yellow Stay Alert;
- the inline alert map and the region list;
- onboarding with the location prompt;
- the iOS launch rules, haptics and en/uk/ru wording.

Screenshot tests render every screen on the JVM at every build (owner, 2026-10-17, answer «А»).

Conventions are spec 001's. A requirement defined in 001 and extended here is amended in place in
001 with a dated note. iOS behaviour without an iOS ID gets an `ANDROID` ID, and the requirement
names the iOS source file.

## User scenarios

1. **The status follows the driver** (P1). The status is for the region the driver is in. Kyiv is
   used with no location, the last region outside Ukraine, and changes are noticed without a modal.
2. **The status stays honest while the screen stays open** (P1). The app refreshes on its own,
   faster during an alarm and slower to save battery. Old data shows its age and never shows green.
3. **Around the region** (P1). Nearby alerts are named. Stay Alert warns a quiet region that is
   surrounded. The map and the list show the whole country.
4. **Details** (P2). Details holds a plain summary of the country, location access, the data source
   and the version.
5. **First launch** (P2). One onboarding page; the location prompt waits for it to finish.

## Requirements: amended in spec 001

- REQ-PROVIDER-002 clause 1 gains the shared periodic refresh (REQ-REFRESH-002), and its trigger
  list gains the inline map's first appearance in a foreground session (REQ-ANDROID-002).
- REQ-REFRESH-006 takes its full iOS text: stale when `now − checkedAt > 2 × current base interval`.
- REQ-SURF-001 gains the short form on the region list's status pills.

## Requirements: new in this spec

### REQ-REFRESH-002 — Adaptive shared polling interval

Status: approved

Core: P2

Source: iOS REQ-REFRESH-002, `docs/requirements/refresh-policy.md`

Given the Status or Details tab is started\
When the next poll is scheduled\
Then the base interval is 60 s, 30 s while the current region is in alarm, and 300 s under battery saver, thermal status severe or worse, or a restricted (Data Saver) metered network, which wins over alarm; each wait has ±10 % jitter; one timer serves the app, and a scheduled poll inside a rate-limit window is skipped

Differs from iOS: Low Power Mode is Android's battery saver (`PowerManager.isPowerSaveMode`). The thermal state `serious` is `THERMAL_STATUS_SEVERE` (API 29+; on API 26–28 not available, treated as normal). The constrained path is Data Saver on a metered network (`ConnectivityManager.restrictBackgroundStatus`). There is no CarPlay to ref-count; one surface, the tab shell, owns the timer.

### REQ-REFRESH-011 — Refresh answers with a haptic only

Status: approved

Core: P1

Source: iOS REQ-REFRESH-011, `docs/requirements/refresh-policy.md`

Given the driver taps Refresh\
When the refresh finishes\
Then a confirm haptic plays whether a request was sent or a hold (REQ-REFRESH-010, REQ-ANDROID-001) kept it, a reject haptic plays only when the provider request failed, and no text message appears; refreshes the driver did not start play no haptic

Differs from iOS: the gesture is the Refresh button, not a pull, and the haptics are Android's `CONFIRM` and `REJECT` feedback constants (API 30+), with a plain click on older versions.

### REQ-REGION-004 — Resolver rules

Status: approved

Core: P2

Source: iOS REQ-REGION-004, `docs/requirements/region-model.md`

Given a reverse-geocoded city and administrative area\
When they name Kyiv city, an oblast, or nothing known\
Then Kyiv city wins over the oblast, oblast names match in Ukrainian and English forms (`Чернігівська обл.`, `Chernihiv Oblast`, spacing, case, apostrophes and dots ignored), and an unknown result keeps the current region

Differs from iOS: the geocoder is Android's `Geocoder` with the `uk-UA` locale; its `locality` and `adminArea` stand for the city and the area.

### REQ-REGION-005 — Location fix filtering and geocode throttle

Status: approved

Core: P1

Source: iOS REQ-REGION-005 (amended 2026-10-15), `docs/requirements/region-model.md`

Given the region follows the location\
When a location fix arrives\
Then fixes less accurate than 1 km or older than 60 s are dropped; reverse geocoding runs only after ≥ 60 s and ≥ 5 km since the last geocode, or after 60 s alone while a candidate region is pending or after a resolve that produced no region; a fix the interval does not yet allow, or whose lookup failed, is evaluated again when the wait ends; each lookup is bounded at 15 s and a timeout counts as a failure

Differs from iOS: updates come from the platform `LocationManager` (network and GPS providers, minimum distance 2000 m), with no Google Play services. "Never pause automatically" has no Android counterpart; updates run while a tab is started.

### REQ-REGION-006 — Region switch hysteresis

Status: approved

Core: P1

Source: iOS REQ-REGION-006, `docs/requirements/region-model.md`

Given a resolved region differs from the current one\
When later resolves agree\
Then the switch commits only after ≥ 90 s or ≥ 5 km from the candidate's origin, any disagreement resets the candidate, and the first resolve of a process commits at once

### REQ-REGION-007 — Region change notice

Status: approved

Core: P1

Source: iOS REQ-REGION-007, `docs/requirements/region-model.md`

Given the tracker commits a new region\
When the switch happens\
Then both tabs show a non-modal, dismissible "Region changed: {region}" notice with no Undo

### REQ-REGION-008 — Outside Ukraine keeps the last region

Status: approved

Core: P1, P2

Source: iOS REQ-REGION-008, `docs/requirements/region-model.md`

Given the location changes from inside Ukraine to outside, or the app starts while already outside (a country code other than `UA`, or none)\
When the location is resolved\
Then the last region stays selected (Kyiv city only if there is none) and the outside-Ukraine sheet appears once, not again while the location stays outside; onboarding wins over the sheet, which shows after Get Started

### REQ-REGION-009 — Location access denied

Status: approved

Core: P1

Source: iOS REQ-REGION-009, `docs/requirements/region-model.md`

Given location access is denied\
When the app needs location\
Then updates stop, the region stays the last one (Kyiv city when there is none), and the Status and Details tabs say that turning on location access gives a more precise region and offer Open Settings

Differs from iOS: Open Settings opens the app's system settings page (`ACTION_APPLICATION_DETAILS_SETTINGS`). There is no CarPlay text.

### REQ-REGION-010 — The location prompt waits for onboarding

Status: approved

Core: P3, P4

Source: iOS REQ-REGION-010, `docs/requirements/region-model.md`

Given a first launch, with onboarding not yet finished\
When the app starts\
Then the status loads at once for the default region, and location updates — with the system permission prompt — start only after the driver taps Get Started

### REQ-SURF-005 — Nearby alerts in every status

Status: approved

Core: P1, P2

Source: iOS REQ-SURF-005 (amended 2026-09-21), `docs/requirements/surfaces-and-pro-gating.md`

Given neighbouring regions are under alert\
When the Status tab shows the current region, quiet or in alarm\
Then a nearby line names them ("Nearby: …", at most two names and "+N more"), from the iOS neighbour table (`NearbyRegionPolicy`, Kyiv city with its wider ring); with none nearby there is no line

### REQ-SURF-010 — Stay Alert when alerts surround a quiet region

Status: approved

Core: P1

Source: iOS REQ-SURF-010 (amended 2026-09-23), `docs/requirements/surfaces-and-pro-gating.md`

Given the current region is quiet on fresh data and at least one neighbour is under alert\
When half or more of its neighbours are under alert, or more than half of the country is, or, for Kyiv city, Kyiv Oblast is\
Then the Status hero shows "Stay Alert" in yellow instead of "No Alert", and the nearby line keeps naming the neighbours; stale data never turns yellow

### REQ-SURF-012 — The Status title area is transparent

Status: approved

Core: P1

Source: iOS REQ-SURF-012, `docs/requirements/surfaces-and-pro-gating.md`

Given the Status tab is shown\
When the hero glows in any status colour\
Then nothing is drawn behind the title: no app bar background and no scroll edge tint, and the glow reaches the top of the screen

### REQ-SURF-013 — The whole map fits before the hero keeps its size

Status: approved

Core: P1

Source: iOS REQ-SURF-013, `docs/requirements/surfaces-and-pro-gating.md`

Given the Status tab with the map card\
When the hero, the lines under it and the full-width map card do not fit the visible height\
Then the hero takes its compact size first; if the map still does not fit, it narrows, centred, to the height left, down to a minimum width (240 dp), below which it keeps full width and the page scrolls

### REQ-LAUNCH-001 — No status colour before status is known

Status: approved

Core: P2

Source: iOS REQ-LAUNCH-001, `docs/requirements/launch-and-cold-start.md`

Given the app launches\
When no current or cached status is known yet\
Then the launch window and every frame until then show only the neutral background and mark, never a status colour

### REQ-LAUNCH-002 — Launch never delays a known status

Status: approved

Core: P1

Source: iOS REQ-LAUNCH-002

Given status becomes known during launch\
When the Status screen appears\
Then nothing adds more than 400 ms before it is fully shown

Differs from iOS: Android plays no cold-start animation at all, which meets the 400 ms ceiling by construction.

### REQ-LAUNCH-003 — No checking sweep with a cached status

Status: approved

Core: P1

Source: iOS REQ-LAUNCH-003

Given a cached status exists at launch, fresh or stale (REQ-ANDROID-006)\
When the app launches\
Then the Status screen shows that status at once, and the refresh that follows is shown by the Refresh button's own Checking state

### REQ-LAUNCH-004 — A stale cache is never green

Status: approved

Core: P2

Source: iOS REQ-LAUNCH-004

Given only a stale cached status exists at launch\
When it is shown\
Then a stale quiet status uses the stale grey and clock, never the clear colour, and a stale alarm stays red

### REQ-LAUNCH-005 — Reduce motion and accessibility

Status: approved

Core: P1

Source: iOS REQ-LAUNCH-005

Given animations are off (`ANIMATOR_DURATION_SCALE` 0) or TalkBack is on\
When status changes\
Then there is no spring or sweep (at most a 200 ms cross-fade), and TalkBack announces the hero once

### REQ-ANDROID-002 — Inline alert map

Status: approved

Core: P1, P2

Source: iOS behaviour without an ID: `RegionalCheck/Data/MapImageSource.swift`, `RegionalCheck/Views/MapViewModel.swift`, `MapCardView.swift`

Given the Status tab with the map card\
When the card appears in a foreground session\
Then the app loads `https://ubilling.net.ua/aerialalerts/?map=nightmode` once, 3 s after the status settles, with no polling and no reload on Refresh. The aspect ratio is reserved at 1000:670 before the image decodes. The caption reads "{n} of 25 regions under alert · {age}", with the image's own fetch time as the age. After a failure the card shows "Couldn't load the map." and a "Refresh map" button, and never a previous image. A new foreground session clears the image and loads again. Tapping the card opens the region list. The image is described to TalkBack by the regions under alert.

Differs from iOS: the app is dark only, so the map is always `nightmode`.

### REQ-ANDROID-003 — Region list

Status: approved

Core: P2

Source: iOS behaviour without an ID: `RegionalCheck/Views/RegionListView.swift`, `RegionalCheck/Data/RegionsListModel.swift`

Given the region list is open\
When it shows the snapshot\
Then it reads only: a current-region card with its status pill, an "Alert active (n)" section first (hidden when empty), then the other regions, each sorted by the localized name, with short-form pills (REQ-SURF-001); each row reads "name, status[, current]" to TalkBack; Back returns to Status

### REQ-ANDROID-004 — Details tab

Status: approved

Core: P2, P3

Source: iOS behaviour without an ID: `RegionalCheck/Views/DetailsView.swift`, `StatusSummaryCard.swift`

Given the Details tab is open\
When it shows the snapshot\
Then it shows, in order, a summary card (the iOS deterministic summary: the current status, how many regions are under alert, and which), the location-access row when access is denied, the data source (Ubilling, linking to its API page) with the informational line (REQ-PROVIDER-003), and the version

Differs from iOS: no on-device language model. The iOS deterministic fallback summary is the only summary.

### REQ-ANDROID-005 — Two tabs, always dark

Status: approved

Core: P3

Source: iOS behaviour without an ID: `RegionalCheck/Views/MainTabView.swift`, `RegionalCheck/App/RegionalCheckApp.swift`

Given the app is open\
When it shows its shell\
Then a bottom navigation bar has two tabs, Status and Details; the app is always dark with the iOS palette; the tab shell owns the periodic refresh and location updates while it is started

### REQ-ANDROID-006 — The last snapshot survives the process

Status: approved

Core: P2

Source: iOS behaviour without an ID: `SharedStore` (App Group snapshot)

Given a snapshot was fetched before\
When the app starts in a new process\
Then the last snapshot, the selected region, the onboarding flag and the rate-limit window are read from private app storage, so the launch rules (REQ-LAUNCH-003, REQ-LAUNCH-004) and the floor have them; a corrupt or missing store reads as empty

## Not applicable on Android

REQ-REGION-002 (migration of iOS v1 storage) and REQ-SURF-002 (no paywall: Android has no Pro at all).

## Success criteria

- **SC-001**: `./gradlew check`, the iOS compile and the trace (all approved IDs of 001 and 002 cited by passed tests) are green in CI.
- **SC-002**: Roborazzi screenshot tests cover every screen and state below, and CI fails on an unintended change:
  - Status in No Alert, Stay Alert, Air Raid Alert, stale, Unavailable, Region Unavailable and Checking;
  - the map loading, loaded and failed;
  - the region list;
  - Details;
  - onboarding;
  - the outside-Ukraine sheet;
  - the region-changed notice;
  - location denied.
- **SC-003**: the owner receives the screenshots after each UI group.
- **SC-004**: on the emulator, with a quiet machine: the live status for the emulator's location, the map, the list and Details.
