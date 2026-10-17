# Implementation plan: project skeleton and status core

Feature: `001-skeleton-and-status-core` · Spec: [spec.md](spec.md) · Tasks: [tasks.md](tasks.md)
Created: 2026-10-17 · Status: approved with the spec on 2026-10-17 (D1 to D5 as recommended)

## Summary

Make the project from the official Kotlin Multiplatform template, add a CI gate and a requirement
trace check, then build the status core test-first: the domain, the Ubilling client with one
transient retry and `Retry-After` backoff, the fetch floor, a status controller, and one Compose
Status screen for Kyiv city with Refresh. Nearly all behaviour lives in `shared/commonMain` and is
tested in `commonTest`; the Android app is a thin screen over one `ViewModel`.

## Technical context

| Item | Choice |
|---|---|
| Language | Kotlin, the version the template generates |
| Modules | `shared` (Kotlin Multiplatform: Android and the template's iOS targets) and `androidApp` (Jetpack Compose) |
| Build | Gradle wrapper, Android Gradle Plugin and version catalog as the template generates them |
| Libraries added (D3) | `kotlinx-coroutines-core`, `kotlinx-coroutines-test`, `kotlinx-serialization-json` and the Kotlin serialization plugin, `kotlinx-datetime`; no HTTP library |
| Testing | `kotlin.test` and `kotlinx-coroutines-test` in `commonTest`, run as Android host tests on the JVM; JVM-only tests in `shared/src/androidHostTest` and `androidApp/src/test`; one instrumented Compose test in `androidApp/src/androidTest` as runtime evidence |
| Android | minSdk 26 if the template sets less (A3); compile and target SDK as generated |
| Storage | none; the snapshot and the rate-limit window live in memory for the process |
| Network | one HTTPS GET to `https://ubilling.net.ua/aerialalerts/`; `INTERNET` permission only |
| Never | no account, no ads, no analytics SDK, no tracking identifiers |

### Assumptions to check when the skeleton exists

| # | Assumption | How to check |
|---|---|---|
| A1 | The wizard generates `androidApp` and `shared` with the Android-KMP library plugin (`com.android.kotlin.multiplatform.library`) on AGP 9. Kotlin's quickstart (dated 2026-10-02) lists those modules but states no AGP version. | Read the generated `gradle/libs.versions.toml` and `shared/build.gradle.kts`. If the template is older, follow Kotlin's [AGP 9 migration guide](https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html). |
| A2 | With host tests enabled on the Android target (`withHostTest`, per the [Android-KMP plugin page](https://developer.android.com/kotlin/multiplatform/plugin)), `commonTest` runs as part of the Android host tests. | A deliberately failing `commonTest` test makes `./gradlew check` fail. |
| A3 | `kotlinx-datetime` time zones on Android need API 26 or core library desugaring. | The library's README at setup time. |
| A4 | On a macOS host, the template's `check` also runs the iOS simulator tests; on Linux the iOS tasks are skipped. | `./gradlew check --dry-run` on the Mac and in the Linux CI job. |
| A5 | Test function names with underscores compile on every target (Kotlin's coding conventions allow underscores in test names; spaces in backticks are not supported on Android). | The iOS test compile in CI. |

## Constitution check

Against `regional-check` `docs/core.md`:

- One current region: Kyiv city, fixed until the location spec; Kyiv is core's no-location region.
- One state: one `StateFlow<StatusState>` from one `ViewModel`.
- One data provider: Ubilling, the default JSON endpoint only.
- One user action: Refresh.
- Never list: nothing in this feature collects data, needs an account or shows ads.
- Priorities: P1 driver attention (one screen, one control, no prompts); P2 honest signal (a
  failed refresh never turns a known status into "Unavailable"; the update time is always shown
  with a status); P4 scope and privacy (one permission, no persistence).

Passes; nothing needs a justification.

## Skeleton (slice 0)

Builds, the emulator and Android Studio runs wait until the owner clears the build machine.
Writing files does not.

1. Owner: install Android Studio (through the kit's Brewfile first) and run its setup wizard, which
   installs the Android SDK and asks to accept its licences. Install the Kotlin Multiplatform IDE
   plugin if Android Studio does not include it.
2. Owner (D1): File › New › New Project › Kotlin Multiplatform, with name "Drive Check", the
   application ID from D2, Android and iOS selected, iOS UI not shared, tests included; saved to a
   scratch folder outside the repository.
3. Agent: copy the generated project into the repository: the Gradle wrapper, settings, root build,
   version catalog, `shared/` and `androidApp/`; drop `iosApp/` (the shipping iOS app is
   `regional-check`), `.idea/` and `local.properties`; merge `.gitignore`. The commit body records
   the Android Studio, plugin and template versions and the wizard options.
4. Agent: check A1 to A4; migrate if A1 fails; enable host tests in `shared` (A2); set minSdk
   (A3).
5. Agent: create the local project marker `.agents/project-context.yaml` with `kind: personal`
   through the kit's project setup (its `install` step needs the owner's yes). The marker is
   ignored by Git.

Fallback for D1: the agent downloads the zip from JetBrains' web wizard after the owner's yes,
unpacks it into its own empty scratch folder, reads it, then continues from step 3.

## Project structure

```text
.github/workflows/gate.yml
scripts/
  spec-trace.py                      # requirement trace check, Python 3 standard library
  tests/test_spec_trace.py           # its fixture tests (unittest)
specs/001-skeleton-and-status-core/  # spec.md, plan.md, tasks.md
shared/src/
  commonMain/kotlin/<package>/
    domain/     AlertRegion, AlertStatus, AlertsSnapshot
    provider/   HttpTransport, HttpRequest, HttpResponse, TransportFailure,
                ProviderFailure, UbillingClient, UbillingFeed, KyivTime, RetryAfter
    refresh/    RefreshPolicy, FetchGate
    status/     StatusState, StatusController
  commonTest/kotlin/<package>/       # fakes, the live-response fixture, the REQ tests
  androidMain/kotlin/<package>/
    provider/   UrlConnectionTransport, AndroidProviderLog
  androidHostTest/kotlin/<package>/  # JVM exception mapping, request properties
androidApp/src/
  main/  MainActivity, StatusViewModel, StatusScreen, res/values{,-uk,-ru}/strings.xml
  test/  string resource wording test
  androidTest/  StatusScreenTest (instrumented, runtime evidence)
```

`<package>` follows the application ID chosen in D2.

## Design

### Provider client (`provider/`)

- `HttpTransport` is the seam, like iOS's `HTTPClient`: `suspend fun execute(request: HttpRequest):
  HttpResponse`. `HttpRequest` carries the URL, the timeout, `bypassCache = true` and the
  `User-Agent`. A transport throws `TransportFailure(kind)`, where `kind` is `Timeout`,
  `ConnectionLost`, `CannotConnect`, `HostLookup` or `Other`.
- `UrlConnectionTransport` (androidMain) runs `HttpURLConnection` on `Dispatchers.IO` with
  `useCaches = false`, connect and read timeouts of 15 s, and the `User-Agent`
  `DriveCheck/<versionName> (Android)`. It maps Java exceptions to kinds as REQ-REFRESH-003 lists.
  The iOS targets get no transport: they only have to compile `commonMain`.
- `UbillingClient.fetchAlerts(): AlertsSnapshot`, one instance per process:
  1. One request. On a transient `TransportFailure`, `delay(2.seconds)` and one more request
     (REQ-REFRESH-003).
  2. 429: increment the rate-limit streak and throw `ProviderFailure.RateLimited(until)`, with
     `until` from `RetryAfter.deadline(header, now, attempt)`.
  3. Any other non-2xx, a non-JSON `Content-Type` or a decode error: throw the matching
     `ProviderFailure`, with a body prefix of at most 240 bytes for the log.
  4. Decode with `kotlinx-serialization-json`, ignoring unknown fields; reset the streak; map keys
     through `AlertRegion.fromApiKey`; log each unknown key once per client instance.
- `KyivTime.parseCachedAt` reads `yyyy-MM-dd HH:mm:ss` in `Europe/Kyiv` with `kotlinx-datetime`;
  `null` when it does not parse.
- `RetryAfter.deadline` mirrors iOS `RetryAfterParser`: delta-seconds or IMF-fixdate HTTP-date
  (`DateTimeComponents.Formats.RFC_1123`), clamped to `[now, now + 5 min]`; otherwise
  `min(5 min, 30 s × 2^(attempt − 1))`.

### Refresh rules (`refresh/`)

- `RefreshPolicy` holds the constants: fetch floor 10 s, transient retry delay 2 s, maximum hold
  5 min, backoff start 30 s.
- `FetchGate` mirrors iOS `FetchGate`: `isHeld(isScheduled, now)` holds a scheduled poll inside the
  rate-limit window and any trigger within 10 s of the last success; `recordSuccess(now)` leaves the
  window alone; `recordRateLimit(until, now)` caps the window at 5 min.

### Status controller (`status/`)

`StatusController(client, clock, scope, region = AlertRegion.KyivCity)` exposes
`state: StateFlow<StatusState>` and three entry points: `onSurfaceStarted()`,
`onSurfaceStopped()` and `refresh()`. The `ViewModel` owns it, with `viewModelScope` as `scope`.

- Starts in `Checking`. A start or a Refresh asks the gate first; a held trigger changes nothing
  (REQ-REFRESH-010).
- Single flight: while a request runs, a new trigger joins it and starts nothing
  (REQ-PROVIDER-002). A stopped surface starts no request; a running one completes
  (REQ-REFRESH-001).
- Success: record it in the gate, keep the snapshot, resolve the region: `Quiet` or `Alarm` with
  `checkedAt`, or `RegionUnavailable` (REQ-REGION-001).
- 429: record the window; `Unavailable` only when no snapshot is held (REQ-REFRESH-005,
  REQ-REFRESH-006).
- Other failures: `Unavailable` only when no snapshot is held (REQ-REFRESH-006).
- `CancellationException` is rethrown and changes nothing (REQ-REFRESH-001).
- Time comes from an injected `kotlin.time.Clock`; waits use `delay`, so tests run in virtual time
  with `runTest`.

### Android app

- `StatusViewModel` builds the controller with the process's single `UbillingClient` and exposes
  its `state`. No other state.
- `StatusScreen`: region name, the full-form status title (REQ-SURF-001) with a status colour and
  an icon (never colour alone), "Updated: HH:mm" in the device's time zone and 12- or 24-hour
  setting, the error hint for `Unavailable`, the Refresh button (D4, at least 48 dp), and the
  informational line (D5). `LifecycleStartEffect` calls `onSurfaceStarted()` and, on stop,
  `onSurfaceStopped()`.
- Semantics: the region, status and update time merge into one TalkBack announcement; the status
  is a polite live region, so a change is announced.
- Strings live in `res/values`, `values-uk` and `values-ru`, worded as REQ-SURF-001's table.

## Test strategy

Tests follow the test pyramid and are sized by risk. Every new test is seen failing once on code
that lacks the behaviour, and the review packet records that run.

| Layer | Where | Covers |
|---|---|---|
| Unit, common | `shared/src/commonTest` | domain, feed decoding, `cachedat` and `Retry-After` parsing, retry, backoff streak, fetch gate, controller states and triggers |
| Concurrency, common | `shared/src/commonTest` | single flight under concurrent triggers, a Refresh during the 2 s retry wait joining the running fetch, cancellation not recorded as failure, retry ordering in virtual time |
| Unit, JVM | `shared/src/androidHostTest` | Java exception to transport kind mapping; request properties of `UrlConnectionTransport` (no connection made) |
| Unit, JVM | `androidApp/src/test` | string resource wording in en, uk, ru (REQ-SURF-001) |
| Instrumented | `androidApp/src/androidTest` | Refresh tap reaches the controller; merged TalkBack semantics. Run on the emulator as runtime evidence, not part of the gate |
| Runtime | emulator | SC-003 and SC-004 |

The feed fixture is one live response recorded once (a single request) and kept as a Kotlin
string in `commonTest`, because `commonTest` has no common file API.

## Requirement trace check

`scripts/spec-trace.py`, Python 3 standard library only, modelled on the iOS `scripts/spec-trace.sh`:

- Definitions: headings `### REQ-<AREA>-<NNN>` in `specs/*/spec.md`, each with a `Status:` line;
  `approved` counts. A second definition of one ID fails.
- Citations: Kotlin functions annotated `@Test` whose names start `REQ_<AREA>_<NNN>_`, in
  `shared/src/*Test/**` and `androidApp/src/test/**`. Instrumented tests do not count.
- Fails when an approved requirement has no citing test, when a test cites an ID no spec defines,
  or on a duplicate definition.
- `--results <dir>`: also reads the Gradle JUnit XML reports under `<dir>`; a citing test counts
  only if it ran and passed, so an ignored or skipped test does not cover a requirement.
- Its own tests: `python3 -m unittest discover -s scripts/tests`, with fixtures in temporary
  folders.

## CI (`.github/workflows/gate.yml`)

- Triggers: pull requests and pushes to `main`. `permissions: contents: read`. A concurrency group
  per ref cancels superseded pull request runs.
- Actions pinned to full commit SHAs, as GitHub's security hardening guide advises for a public
  repository; `gradle/actions/setup-gradle` for caching and wrapper validation.
- Job `check` on `ubuntu-latest`: JDK as the template's toolchain needs; `./gradlew check`; the
  trace check's own tests; `python3 scripts/spec-trace.py --results .`.
- Job `ios-compile` on `macos-latest`: `./gradlew :shared:compileKotlinIosSimulatorArm64
  :shared:compileTestKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64` (task names as the
  template's targets give them). Compile only: the owner decided the iOS targets are not run.
- `AGENTS.md` names the same three parts of the gate.

## Review packet

The pull request description carries, per commit group: the task IDs, the diff summary, the tests
that prove it (with the failing run seen first), review findings and their outcome, runtime
evidence, and risk. Merge only after the owner approves.

## Risks

| Risk | Mitigation |
|---|---|
| Ubilling's per-host limit counts a carrier NAT address as one host, shared by many devices | Per-device load is bounded (REQ-PROVIDER-002); the shared-address case is outside a client's control and applies to the iOS app equally. Re-check the wiki before the release |
| The template differs from A1 (older AGP, one module) | Kotlin's AGP 9 migration guide; the change stays inside the skeleton commit |
| `HttpURLConnection` classifies some failures differently from `URLError` | The mapping is explicit and tested (REQ-REFRESH-003); unknown exceptions are not transient, so the default is the conservative one |
| Builds wait for the build machine | Spec, plan and tasks need no build; the skeleton and every test run wait |
| Kotlin and library versions move between today and the skeleton | Versions come from the template and the libraries' current stable releases on the day, recorded in the skeleton commit |
