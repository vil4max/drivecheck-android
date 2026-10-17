# Implementation plan: phone parity

Feature: `002-phone-parity` · Spec: [spec.md](spec.md) · Tasks: [tasks.md](tasks.md) · Created: 2026-10-17

## Technical context

The stack is spec 001's. These additions follow the owner's answers:

| Addition | Why | Version |
|---|---|---|
| Roborazzi (`roborazzi`, `roborazzi-compose`, Gradle plugin) and Robolectric, test only | Screenshot tests on the JVM (owner, «А») | Roborazzi 1.76.0, Robolectric 4.17 (current stable releases) |
| `androidx.compose.ui:ui-test-junit4` and `ui-test-manifest`, test only | Compose rule for the screenshot tests | from the Compose BOM |

There is no other library. Location uses the platform `LocationManager` and `Geocoder`, without
Google Play services. Storage uses private `SharedPreferences` with kotlinx-serialization JSON.
Navigation is plain Compose state with `BackHandler`. Haptics use `View.performHapticFeedback`.

## Design

- `shared/commonMain`:
  - `refresh/RefreshPolicy`: intervals, jitter, environment.
  - `refresh/PeriodicRefresh`: one loop on the controller's scope.
  - `status/StatusPresentation`: phase, stale, Stay Alert, nearby line.
  - `domain/NearbyRegionPolicy`: the iOS table.
  - `region/AlertRegionResolver`, `region/RegionTracker`: filtering, throttle, hysteresis, outside Ukraine.
  - `status/CountrySummary`: the deterministic summary.
  - `store/StatusStore`: an interface for the snapshot, region, onboarding flag and 429 window.
  - `map/MapController`: once per session, 3 s after settle, failure state.
- `shared/androidMain`:
  - `PreferencesStatusStore`.
  - `AndroidRefreshEnvironment`: power saver, thermal, Data Saver.
  - `PlatformLocationSource` and `PlatformGeocoder`, with a 15 s bound.
- `androidApp`:
  - `AppShell` with two tabs, the notice host and the sheet host.
  - Screens: `StatusScreen` (hero, nearby line, denied card, map card), `RegionListScreen`, `DetailsScreen`, `OnboardingScreen`, `OutsideUkraineSheet`.
  - One `AppViewModel` exposing one `StateFlow<AppState>`.
- Strings: every iOS phone key the screens need, in en, uk and ru, copied from the iOS catalogs.

## Tests

- **commonTest**: every shared REQ with a virtual clock, the same way as in spec 001.
- **androidHostTest**: environment mapping and the store round-trip.
- **androidApp/src/test**: Roborazzi screenshot tests of every screen and state in SC-002, plus the string wording tests.
  - `./gradlew check` runs `verifyRoborazziDebug`.
  - The recorded images live in `androidApp/src/test/screenshots/`.
- **Emulator**: runtime checks only, on a quiet machine.
