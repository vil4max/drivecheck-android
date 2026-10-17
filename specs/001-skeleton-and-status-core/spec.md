# Feature specification: project skeleton and status core

Feature: `001-skeleton-and-status-core` · Created: 2026-10-17
Status: approved by the owner at the start gate on 2026-10-17, together with `tasks.md`
Input: the owner's Android plan of 2026-10-17, slice 0 (project setup and CI) and slice 1 (status core)

## Summary

The repository gets its Kotlin Multiplatform skeleton (a `shared` module and a Jetpack Compose
`androidApp`), a GitHub Actions gate, and a requirement trace check. On that skeleton, the first
usable build shows one thing: whether Kyiv city is under an air raid alert, with the time the
provider last updated it, and one action, Refresh. Location, freshness tiers, widgets, the car
screen and notifications are later specs.

Requirement text comes from the iOS app, `regional-check` `docs/requirements/`. Each requirement
below keeps its iOS ID, states its Android text, and says what differs and why.

## User scenarios and testing

### User story 1: see Kyiv city's alert status at a glance (priority P1)

A driver opens Drive Check on an Android phone and sees whether Kyiv city is under an air raid
alert and when the data was last updated. They tap Refresh to check again.

Why this priority: it is the free signal the product exists for (core P2).

Independent test: with a fake provider, the screen state goes from Checking… to No Alert or Air
Raid Alert with the update time; on the emulator, the live status matches the provider's
`м. Київ` entry.

Acceptance scenarios:

1. Given the feed reports `м. Київ` with `alertnow: true`, when the Status screen opens, then it
   shows "Air Raid Alert" for Kyiv and "Updated: HH:mm" from `cachedat`.
2. Given the feed reports `alertnow: false`, when the screen opens, then it shows "No Alert".
3. Given the last successful fetch was more than 10 s ago, when the driver taps Refresh, then one
   request goes out and the screen shows the new answer.
4. Given a fetch succeeded less than 10 s ago, when the driver taps Refresh, then no request goes
   out and the status stays as it is.
5. Given the feed has no `м. Київ` key, when the answer arrives, then the screen shows "Region
   Unavailable", not "Unavailable".
6. Given no answer has arrived yet in this process, when the request fails, then the screen shows
   "Unavailable" and "Tap Refresh to try again".
7. Given the screen shows a status, when a later refresh fails, then the status and its update
   time stay.

### User story 2: the provider is not overloaded (priority P1)

Ubilling serves the data for free and publishes a per-host rate limit, with a possible permanent
ban above it. A second client app adds requests, so the Android app has to prove its load, not
assume it.

Independent test: a counted fixture session (REQ-PROVIDER-002) and the retry and backoff tests.

Acceptance scenarios:

1. Given a request times out, when it fails, then the app retries once after 2 s and no more.
2. Given the provider answers HTTP 429 with `Retry-After: 45`, when the answer arrives, then the
   rate-limit window ends 45 s later and the screen keeps its last status (or shows "Unavailable"
   when it has none).
3. Given a request is in flight, when the driver taps Refresh, then no second request goes out.

### User story 3: every change passes the same gate (priority P2)

The owner merges a pull request only when the same gate passes in CI as on the Mac: Android
checks, the iOS-target compile of `shared`, and the requirement trace check.

Independent test: the trace check's own fixture tests; one green CI run on this feature's pull
request.

Acceptance scenarios:

1. Given an approved requirement that no test name cites, when the trace check runs, then it fails
   and names the requirement.
2. Given a test name that cites an ID no spec defines, when the trace check runs, then it fails.
3. Given `commonMain` code that does not compile for iOS, when CI runs, then the iOS compile job
   fails.

### Edge cases

- The feed carries keys that are not app regions (`Севастополь` today): they are ignored and each
  is logged once per process (REQ-REGION-001).
- `cachedat` does not parse: `checkedAt` falls back to the local fetch time (REQ-PROVIDER-001).
- `cachedat` falls on either side of a Europe/Kyiv daylight-saving change: the offset follows the
  zone rules, never a fixed +02:00 or +03:00.
- A non-2xx answer other than 429, a non-JSON `Content-Type` or an undecodable body is a failure
  and is not retried (REQ-REFRESH-003).
- `Retry-After` more than 5 min ahead is capped at 5 min; a past or negative value counts as now;
  an unparseable or non-finite one falls back to the backoff (REQ-REFRESH-005).
- The device has no network: Android reports a failed host lookup, so the request gets its one
  transient retry (REQ-REFRESH-003, see "Differs from iOS").
- The screen stops while a request is in flight: the request completes and updates the state; no
  new request starts while the screen is stopped. The screen closing for good cancels the request,
  and a cancelled request is not a failure (REQ-REFRESH-001).
- Rotation or a quick switch away and back restarts the screen: the fetch floor holds the restart
  when the last success is less than 10 s old (REQ-REFRESH-010).

## Requirements

Conventions:

- `Status: proposed` until the owner approves this spec; then `approved`. The trace check counts
  approved requirements only.
- A requirement shared with iOS keeps its iOS ID and title and names its source. Its Android
  text is normative; "Differs from iOS" says what changed and why.
- An Android-only requirement gets an ID in the `ANDROID` area (`REQ-ANDROID-NNN`), which iOS does
  not use. This feature needs none.
- A test cites a requirement in its function name, in the form `REQ_PROVIDER_001_<behaviour>`.
- A requirement is defined once, in the spec that introduces it. A later spec that extends it
  amends that definition in place with a dated note, as the iOS documents do.

### REQ-PROVIDER-001 — Default JSON endpoint

Status: approved

Core: P2, P3

Source: iOS REQ-PROVIDER-001 (approved), `docs/requirements/aerial-alerts-provider.md`

Given the app needs status\
When it requests the provider\
Then it sends a GET to the default JSON endpoint `https://ubilling.net.ua/aerialalerts/` with no
query parameters, bypassing any local HTTP cache, with a 15 s timeout and a `User-Agent` that names
Drive Check for Android and its version; it reads `states[region].alertnow` and `cachedat`
(`YYYY-MM-DD HH:mm:ss` in Europe/Kyiv); `checkedAt` is `cachedat` when it parses, else the local
fetch time; and a non-2xx status other than 429, a non-JSON `Content-Type` or an undecodable body
is a provider failure

Differs from iOS: the iOS text names the endpoint and the two fields only. The cache, timeout,
`checkedAt` and failure rules are iOS behaviour from `refresh-policy.md` ("Freshness") and
`UbillingProvider`, written into the requirement here so the Android tests can cite them. The
`User-Agent` is Android-only: iOS's default user agent already names the app, while Android's
default (`Dalvik/…`) names no app, and an admin who may ban a host should be able to tell which
client it is.

### REQ-PROVIDER-002 — Polite load

Status: approved

Core: P2, P4

Source: iOS REQ-PROVIDER-002 (approved), `docs/requirements/aerial-alerts-provider.md`

Grounded in Ubilling's published limits, re-checked on 2026-10-17 against the
[API wiki page](https://wiki.ubilling.net.ua/doku.php?id=aerialalertsapi) (last edited
2026-09-07): 2 requests per second per host since 2024-02-13; over it, HTTP 429 and possibly a
permanent ban; raw data cached server-side for 3 s; no keys and no registration. The page
publishes no other terms of use, and it calls the API informational only, to be checked against
official sources.

Given the Status screen, the only surface of this feature\
When the app issues provider requests\
Then all three clauses hold:

1. **Every request has an enumerated trigger**: the Status screen starting (REQ-REFRESH-001) or
   the driver's Refresh. No composition, recomposition or render path fetches. A trigger that
   arrives while a request is in flight joins that request and sends no second one.
2. **Counted, not assumed.** In a fixture session of a screen start, a Refresh after the fetch
   floor, a Refresh inside the floor and a Refresh during an in-flight request, the number of
   provider requests equals the number of triggers that neither the fetch floor (REQ-REFRESH-010)
   nor an in-flight request held, plus the transient retries REQ-REFRESH-003 allows.
3. **HTTP 429 is honoured** (REQ-REFRESH-005).

Differs from iOS: the iOS clause 1 (one shared periodic timer) and the CarPlay, widget, Siri,
Live Activity and map triggers have no Android surface yet. Each later spec that adds a surface or
the periodic refresh amends this trigger list. The rate argument: after a success, the floor
allows at most one request per 10 s per device whatever the triggers, a twentieth of the 2 rps
host limit; after a failure, attempts are bounded by the driver's taps and one 2 s retry each.

### REQ-REFRESH-001 — Fetch only for an active surface

Status: approved

Core: P2, P4

Source: iOS REQ-REFRESH-001 (approved), `docs/requirements/refresh-policy.md`

Given the Status screen\
When it starts (it opens, or returns to the foreground) or the driver taps Refresh\
Then the app fetches at once, held only by the fetch floor (REQ-REFRESH-010), and sends no request
while the screen is stopped; a request in flight when the screen stops completes, and a request
cancelled because the screen closed for good is not recorded as a failure

Differs from iOS: Android's "appears" is the screen's lifecycle reaching the started state.
Refresh is a button, where the iOS phone uses pull to refresh (decision D4). There is no CarPlay
session; Android's car screen is a later spec. The cancellation clause is iOS behaviour
(`StatusController`: a withdrawn request is not a failed one) made explicit.

### REQ-REFRESH-003 — One retry for transient errors

Status: approved

Core: P2

Source: iOS REQ-REFRESH-003 (approved), `docs/requirements/refresh-policy.md`

Given a fetch fails with a transient transport error: a timeout, a connection lost mid-exchange,
a host that cannot be connected to, or a failed host lookup\
When the failure happens\
Then the app retries once after 2 s and makes no further attempt for that trigger; HTTP errors,
429 and undecodable bodies are not retried

Differs from iOS: iOS names four `URLError` codes (`timedOut`, `networkConnectionLost`,
`cannotConnectToHost`, `dnsLookupFailed`). Android classifies Java I/O exceptions instead:
`SocketTimeoutException` is a timeout; `ConnectException` and `NoRouteToHostException` are a host
that cannot be connected to; `UnknownHostException` is a failed host lookup; any other
`SocketException`, or an `EOFException`, is a connection lost. TLS failures are not transient.
Android also throws `UnknownHostException` when the device has no network, which iOS reports as
`notConnectedToInternet` and does not retry, so an offline Android device makes one extra attempt
2 s later. That attempt cannot reach Ubilling, so it adds no load.

### REQ-REFRESH-005 — Rate limit backoff

Status: approved

Core: P2

Source: iOS REQ-REFRESH-005 (approved, amended 2026-10-15), `docs/requirements/refresh-policy.md`

Given the provider returns HTTP 429\
When the next request is due\
Then the app waits for `Retry-After` (seconds or HTTP-date) or backs off 30 s → 60 s → … up to
5 min, skips scheduled polls in that window, and still allows a manual refresh

Carried over from the iOS amendment of 2026-10-15: a `Retry-After` more than 5 min ahead is capped
at 5 min; a past or negative one counts as now; a non-finite or unparseable one falls back to the
backoff; consecutive 429s escalate the backoff and a decoded success resets it; a successful manual
refresh does not end the window early.

Differs from iOS: the window lives in memory for the process; iOS also stores it in the App Group
for the widget and Siri, which have no Android counterpart yet. This feature has no scheduled
poll, and its two triggers are the kinds iOS lets through a window (the iOS trigger table: "Phone
becomes active" and "Driver's Refresh"), so in this feature the window holds no request. The rule
that holds scheduled polls is built and tested now as a pure rule, for the periodic refresh to use.

### REQ-REFRESH-006 — Stale threshold

Status: approved — this feature adopts the rule's last two sentences only; see "Differs from iOS"

Core: P2

Source: iOS REQ-REFRESH-006 (approved), `docs/requirements/refresh-policy.md`

Given the Status screen holds a snapshot\
When a later request fails\
Then the screen keeps the last known alert phase with its update time; "Unavailable" is reserved
for a failed request when no snapshot exists, and a failed request never replaces a clear or alert
phase with an unavailable one

Differs from iOS: the iOS Given/When/Then marks a snapshot stale when `now − checkedAt` exceeds 2 ×
the current base interval. That interval belongs to the periodic refresh (REQ-REFRESH-002), which
this feature does not have, so the stale marker waits for the spec that adds the periodic refresh;
that spec amends this requirement to the full iOS text. Until then the update time is the only age
signal on the screen.

### REQ-REFRESH-010 — Fetch floor and cache serving

Status: approved

Core: P2

Source: iOS REQ-REFRESH-010 (approved), `docs/requirements/refresh-policy.md`

Given a fetch succeeded less than 10 s ago in this process\
When any trigger asks for a refresh: the screen starting or Refresh\
Then no request is sent, the held snapshot stays in place, and the refresh completes without
recording a failure; the first fetch of a process and any retry after a failed fetch are never
subject to the floor, because the floor is measured from the last successful fetch

Differs from iOS: an iOS "session" is an Android process here, since the snapshot is held in
memory only; a new process has no snapshot and fetches at once. "Marking the data stale" is left
out until a stale state exists (REQ-REFRESH-006). The trigger list is this feature's two.

### REQ-REGION-001 — Catalog of 25 regions

Status: approved

Core: P2

Source: iOS REQ-REGION-001 (approved), `docs/requirements/region-model.md`

Given a provider response\
When it contains unknown keys or lacks the selected region\
Then unknown keys are ignored and each is logged once per process, and a missing selected region
shows "Region Unavailable", distinct from a network error ("Unavailable")

Differs from iOS: the catalog is the iOS `AlertRegion` table, the same 25 regions with the same
Ubilling keys, pinned by a fixture of a live response. In this feature the selected region is
always Kyiv city (`м. Київ`); location to region is the next spec. iOS logs at notice level; the
Android log is at info level, the closest Android priority.

### REQ-SURF-001 — Full and short status forms, one wording each

Status: approved

Core: P2

Source: iOS REQ-SURF-001 (approved), `docs/requirements/surfaces-and-pro-gating.md`

Given a status\
When the Status screen shows it\
Then its title uses the full form, worded as on iOS in every locale the app ships (en, uk, ru); a
status word standing alone is Title Case and an explaining sentence is sentence case

| Phase | en | uk | ru |
|---|---|---|---|
| Alarm (full form) | Air Raid Alert | Повітряна тривога | Воздушная тревога |
| Quiet | No Alert | Тривоги немає | Тревоги нет |
| Checking | Checking… | Перевіряємо… | Проверяем… |
| Unavailable | Unavailable | Недоступно | Недоступно |
| Region unavailable | Region Unavailable | Регіон недоступний | Регион недоступен |
| Update line | Updated: %s | Оновлено: %s | Обновлено: %s |
| Region | Kyiv | Київ | Киев |
| Action | Refresh | Оновити | Обновить |
| Error hint | Tap Refresh to try again | Натисніть «Оновити», щоб спробувати знову | Нажмите «Обновить», чтобы попробовать снова |

Differs from iOS: only the phone Status screen exists, so only the full form is used; the short
form arrives with the widget spec. The error hint names the Refresh button, where iOS says "Pull to
refresh and try again"; its uk and ru text is new, approved by the owner at the start gate.

### REQ-PROVIDER-003 — Informational source

Status: approved (decision D5)

Core: P2

Source: iOS REQ-PROVIDER-003 (approved), `docs/requirements/aerial-alerts-provider.md`

Given the app shows provider data\
When the Status screen is open\
Then one line below Refresh states that the data is informational, as the provider does, with the
iOS wording: "Alert data is informational only. Do not rely on it for critical decisions." (uk:
"Дані тривог мають інформаційний характер. Не покладайтеся на них при критичних рішеннях."; ru:
"Данные тревог носят информационный характер. Не полагайтесь на них при критических решениях.")

Differs from iOS: iOS states this on the Details tab. This feature has one screen, and the closed
test on Google Play starts with this build, so testers see the provider's warning from the first
install.

Look (owner, 2026-10-17): the Status screen uses the iOS app's palette and, like it, is dark only;
status colours are never themed, and there is no wallpaper-based dynamic colour. The colour only
repeats what the title says.

## Key entities

- `AlertRegion`: the 25 app regions, each with its Ubilling key.
- `AlertStatus`: `quiet` or `alarm`.
- `AlertsSnapshot`: one decoded feed: `source`, `serverCachedAt` (nullable), `fetchedAt`, a status
  per region, and `checkedAt` (`serverCachedAt` else `fetchedAt`).
- `StatusState`: what the screen renders: `Checking`, `Quiet(checkedAt)`, `Alarm(checkedAt)`,
  `Unavailable`, `RegionUnavailable`.
- `ProviderFailure`: transient transport failure (with its kind), other transport failure, HTTP
  status, non-JSON body, undecodable body, or rate limited until a time.
- `FetchGate`: the last success time and the rate-limit window, deciding whether a trigger is held.

## Success criteria

- **SC-001**: on a clean checkout, `./gradlew check` passes on the Mac and in GitHub Actions, and
  the macOS CI job compiles `shared` (main and test sources) for its iOS targets.
- **SC-002**: the trace check fails on fixtures with an uncited approved requirement, an unknown ID
  or a duplicate definition, and passes on this repository with every approved requirement of this
  spec cited by a host test that ran and passed.
- **SC-003**: on the emulator, the screen shows Kyiv city's live status and update time after the
  first fetch; a first launch with the network off shows "Unavailable"; a Refresh less than 10 s
  after a success sends no request, counted in the client's log.
- **SC-004**: TalkBack reads the region, status and update time; Refresh has a label and a touch
  target of at least 48 dp; at the largest system font size no text is clipped.
- **SC-005**: Ubilling's published terms are checked and recorded in REQ-PROVIDER-002 before the
  first build reaches a tester (done 2026-10-17; re-checked before the Google Play release).

## Out of scope

Location to region and the Kyiv fallback (REQ-REGION-004 to REQ-REGION-010, next spec); the
periodic refresh (REQ-REFRESH-002) and the stale marker; freshness tiers and a home-screen widget;
the car screen; an ongoing alert notification; persistence of the snapshot or the rate-limit window
across process death; nearby alerts (REQ-SURF-005) and the Stay Alert status (REQ-SURF-010); the
alert map; any Pro item; the Google Play release itself.

## Decisions for the start gate

| # | Decision | Recommended | Alternative |
|---|---|---|---|
| D1 | How the skeleton is made | Android Studio's Kotlin Multiplatform wizard, run by the owner (the documented path; Android Studio is needed anyway for the SDK and the emulator) | JetBrains' web wizard: the agent downloads its zip after the owner's yes |
| D2 | Application ID, permanent once on Google Play | `io.github.vil4max.drivecheck` (a reverse domain the owner controls through GitHub) | `vil4max.drivecheck`, mirroring the iOS bundle ID `vil4max.RegionalCheck` |
| D3 | Libraries beyond the template | `kotlinx-coroutines` (core, test), `kotlinx-serialization-json` with its compiler plugin, `kotlinx-datetime`; HTTP over the platform's `HttpURLConnection`, no HTTP library; minSdk 26 if the template sets less | Ktor client (core, OkHttp and Darwin engines, mock engine) for HTTP |
| D4 | The Refresh control | A visible Refresh button: one obvious action, reachable with TalkBack | Pull to refresh, as on the iOS phone |
| D5 | The informational line (REQ-PROVIDER-003) | On the Status screen in this feature | Wait for a Details screen spec; REQ-PROVIDER-003 leaves this spec |

Owner's answers at the start gate, 2026-10-17: D1 to D5 as recommended, and the uk and ru error
hint as written.
