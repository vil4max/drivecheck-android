# Tasks: project skeleton and status core

Feature: `001-skeleton-and-status-core` · Spec: [spec.md](spec.md) · Plan: [plan.md](plan.md)
Created: 2026-10-17 · Status: approved by the owner at the start gate on 2026-10-17

One pull request for the feature; one commit per group, in the order "Dependencies" gives. Inside a
group, each test task
comes first and is seen failing before its code task; the review packet records the failing run.
`[P]` marks a task that can run in parallel with the previous one (different files, no
dependency). `[Owner]` marks a task only the owner can do. Test names below are the planned
function names, each citing its requirement.

Nothing in groups 1 to 7 starts before the owner approves this spec and task list. Every task that
runs Gradle, the emulator or Android Studio waits until the owner clears the build machine.

## Group 0 — spec

Commit: `docs(specs): specify the skeleton and status core`

- [x] T001 [Owner] Start gate: approve `spec.md` and this task list together, and answer D1 to D5.
- [x] T002 Record the answers in `spec.md` ("Decisions for the start gate"), set each requirement
  the owner approved to `Status: approved`, and drop REQ-PROVIDER-003 if D5 goes the other way.

## Group 1 — skeleton

Commit: `build: create the project from the Kotlin Multiplatform template`

- [ ] T003 [Owner] Install Android Studio through the kit's Brewfile and run its setup wizard (SDK,
  licences); install the Kotlin Multiplatform IDE plugin if it is not included.
- [ ] T004 [Owner] Run the wizard as `plan.md` "Skeleton" step 2 says, into a scratch folder (D1;
  the web wizard fallback needs the owner's yes for the download).
- [ ] T005 Copy the generated project into the repository as `plan.md` step 3 says; record the
  versions and wizard options in the commit body.
- [ ] T006 Check assumptions A1 to A4 and record the results in `plan.md`; migrate to the AGP 9
  layout if A1 fails; enable host tests in `shared`; set minSdk per D3.
- [ ] T007 Add the D3 libraries to `gradle/libs.versions.toml` at their current stable versions,
  with the serialization plugin in `shared`.
- [ ] T008 Prove A2: add a temporary failing test in `shared/src/commonTest`, see
  `./gradlew check` fail, remove it, see `./gradlew check` pass.
- [x] T009 Check that the local project marker `.agents/project-context.yaml` (`kind: personal`)
  is present in the checkout that builds. Project setup already wrote it in the main checkout; an
  app-made worktree gets a copy, since the file is ignored by Git. Not committed.

## Group 2 — gate

Commit: `ci: run the gate and the requirement trace check`

- [x] T010 Write `scripts/tests/test_spec_trace.py` first: an approved requirement with no citing
  test fails; a test citing an undefined ID fails; a duplicate definition fails; an uncited
  proposed requirement passes; with `--results`, a skipped or failed citing test does not count and
  a passed one does. See it fail with no script.
- [x] T011 Write `scripts/spec-trace.py` (plan.md "Requirement trace check") until T010 passes.
- [x] T012 Run it on the repository: no approved requirement is cited yet, so it must fail on the
  approved IDs of this spec; record the output. Groups 3 to 7 turn it green.
- [x] T013 Write `.github/workflows/gate.yml` (plan.md "CI") with actions pinned to commit SHAs.
  Its JDK version and Gradle task names are checked against the skeleton in T006.
- [x] T014 [P] Update `AGENTS.md` "Gate" to name the three parts: `./gradlew check`, the iOS-target
  compile of `shared`, and the trace check.

Until group 7 lands, the trace job is expected to fail on this pull request: the trace check
reports exactly which approved requirements are still uncited.

## Group 3 — domain

Commit: `feat(shared): add the alert domain and the region catalog`

- [ ] T015 Tests in `shared/src/commonTest/.../domain/`:
  - `REQ_REGION_001_catalog_has_25_regions_with_their_ubilling_keys`
  - `REQ_REGION_001_an_unknown_key_maps_to_no_region`
  - `REQ_PROVIDER_001_checked_at_is_cachedat_else_the_fetch_time`
- [ ] T016 Implement `AlertRegion`, `AlertStatus`, `AlertsSnapshot` in `shared/src/commonMain/.../domain/`.

## Group 4 — feed

Commit: `feat(shared): read the Ubilling feed through a transport seam`

- [ ] T017 Record one live response as the fixture `shared/src/commonTest/.../provider/UbillingFixtures.kt`
  (one request; at least the 25 app keys and `Севастополь`), with fakes `FakeTransport` and
  `FakeClock`.
- [ ] T018 Tests in `shared/src/commonTest/.../provider/`:
  - `REQ_PROVIDER_001_requests_the_default_json_endpoint_without_cache` (URL, GET, no query,
    `bypassCache`, 15 s timeout, `User-Agent`)
  - `REQ_PROVIDER_001_reads_alertnow_for_every_catalog_region`
  - `REQ_PROVIDER_001_reads_cachedat_in_kyiv_time_across_daylight_saving` (one winter, one summer
    timestamp)
  - `REQ_PROVIDER_001_an_unparseable_cachedat_falls_back_to_the_fetch_time`
  - `REQ_PROVIDER_001_non_2xx_non_json_and_undecodable_answers_fail`
  - `REQ_REGION_001_unknown_keys_are_ignored_and_each_is_logged_once`
- [ ] T019 Implement `HttpTransport`, `HttpRequest`, `HttpResponse`, `TransportFailure`,
  `ProviderFailure`, `UbillingFeed`, `KyivTime` and `UbillingClient` (no retry yet).
- [ ] T020 [P] Tests in `shared/src/androidHostTest/.../provider/`:
  - `REQ_PROVIDER_001_url_connection_disables_caches_and_sets_timeouts_and_user_agent` (inspects a
    configured connection without connecting)
- [ ] T021 Implement `UrlConnectionTransport` and `AndroidProviderLog` in `shared/src/androidMain/`.

## Group 5 — retry and backoff

Commit: `feat(shared): retry transient failures once and back off on HTTP 429`

- [ ] T022 Tests in `shared/src/commonTest/.../provider/`, in virtual time:
  - `REQ_REFRESH_003_a_transient_failure_is_retried_once_after_two_seconds` (each of the four
    kinds)
  - `REQ_REFRESH_003_a_second_transient_failure_is_final`
  - `REQ_REFRESH_003_http_errors_429_and_bad_bodies_are_not_retried`
  - `REQ_REFRESH_005_a_429_waits_out_retry_after_in_seconds`
  - `REQ_REFRESH_005_a_429_waits_out_retry_after_as_an_http_date`
  - `REQ_REFRESH_005_retry_after_beyond_five_minutes_is_capped`
  - `REQ_REFRESH_005_a_past_or_negative_retry_after_counts_as_now`
  - `REQ_REFRESH_005_an_unusable_retry_after_falls_back_to_the_backoff`
  - `REQ_REFRESH_005_consecutive_429s_escalate_and_a_success_resets_the_backoff`
- [ ] T023 Implement the retry in `UbillingClient`, `RetryAfter` and the rate-limit streak.
- [ ] T024 [P] Tests in `shared/src/androidHostTest/.../provider/`:
  - `REQ_REFRESH_003_java_io_failures_map_to_transient_kinds` (`SocketTimeoutException`,
    `ConnectException`, `NoRouteToHostException`, `UnknownHostException`, `SocketException`,
    `EOFException`)
  - `REQ_REFRESH_003_tls_and_unknown_failures_are_not_transient`
- [ ] T025 Implement the mapping in `UrlConnectionTransport`.

## Group 6 — refresh rules and status controller

Commit: `feat(shared): drive the status from refresh triggers`

- [ ] T026 Tests in `shared/src/commonTest/.../refresh/`:
  - `REQ_REFRESH_010_every_trigger_is_held_within_ten_seconds_of_a_success`
  - `REQ_REFRESH_010_the_first_fetch_and_a_retry_after_a_failure_are_not_held`
  - `REQ_REFRESH_005_the_window_holds_scheduled_polls_and_lets_a_manual_refresh_through`
  - `REQ_REFRESH_005_a_successful_manual_refresh_does_not_end_the_window`
- [ ] T027 Implement `RefreshPolicy` and `FetchGate`.
- [ ] T028 Tests in `shared/src/commonTest/.../status/`, in virtual time:
  - `REQ_REFRESH_001_a_started_surface_fetches_at_once`
  - `REQ_REFRESH_001_refresh_fetches_at_once`
  - `REQ_REFRESH_001_a_stopped_surface_sends_no_request`
  - `REQ_REFRESH_001_a_request_running_when_the_surface_stops_completes`
  - `REQ_REFRESH_001_a_cancelled_request_is_not_a_failure`
  - `REQ_REGION_001_a_feed_without_kyiv_city_shows_region_unavailable`
  - `REQ_REFRESH_006_a_failed_refresh_keeps_the_last_status_and_its_time`
  - `REQ_REFRESH_006_a_failure_without_a_snapshot_shows_unavailable`
  - `REQ_REFRESH_005_a_429_without_a_snapshot_shows_unavailable_and_opens_the_window`
  - `REQ_REFRESH_010_a_held_refresh_keeps_the_snapshot_and_records_no_failure`
  - `REQ_PROVIDER_002_requests_equal_the_triggers_not_held` (the counted fixture session)
  - `REQ_PROVIDER_002_a_trigger_during_a_request_joins_it` (concurrent triggers, including one
    during the 2 s retry wait)
- [ ] T029 Implement `StatusState` and `StatusController`.

## Group 7 — Status screen

Commit: `feat(android): show Kyiv city's alert status with Refresh`

- [ ] T030 Tests in `androidApp/src/test/`, reading the three `strings.xml` files:
  - `REQ_SURF_001_status_wording_matches_ios_in_en_uk_ru`
  - `REQ_PROVIDER_003_the_informational_line_matches_ios_in_en_uk_ru` (only if D5 keeps it)
- [ ] T031 Implement the strings, `StatusViewModel`, `StatusScreen`, `MainActivity` wiring, the
  `INTERNET` permission and the app label; remove the template's sample code and its test.
- [ ] T032 Run `python3 scripts/spec-trace.py --results .` after `./gradlew check`: every approved
  requirement of this spec is cited by a passed host test.
- [ ] T033 [P] Instrumented test `androidApp/src/androidTest/.../StatusScreenTest.kt`: a Refresh tap
  reaches the controller; region, status and update time merge into one TalkBack node. Run with
  `./gradlew :androidApp:connectedAndroidTest` on the emulator.
- [ ] T034 Runtime evidence on the emulator for the review packet: the live status with its update
  time; a first launch with the network off; a Refresh inside the floor sending no request
  (client log); TalkBack; the largest font size (SC-003, SC-004).
- [ ] T035 [P] Update `README.md` "Status" to say what the first build does.

## Pull request

- [ ] T036 Push the branch (kit private-data scan first, as `AGENTS.md` requires) and open one pull
  request with the review packet (plan.md "Review packet"); CI green on both jobs.
- [ ] T037 [Owner] Review and approve the merge. The closed test on Google Play can start from
  this build; the developer account and the testers are the owner's.

## Dependencies

- Group 0 before everything; T003 and T004 before T005; group 1 before groups 3 to 7.
- Group 2 needs no skeleton and lands before group 1 (owner, 2026-10-17).
- Groups 3 → 4 → 5 → 6 → 7 in order.
- T020 and T021 need T019's types; T024 and T025 need T021.
