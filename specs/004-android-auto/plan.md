# Implementation plan: Android Auto

Feature: `004-android-auto` · Spec: [spec.md](spec.md) · Tasks: [tasks.md](tasks.md)

## Design

- **Dependency.** `androidx.car.app:app` 1.7.0, the current stable release, with `app-testing` for
  the tests. Pinned in `gradle/libs.versions.toml`.
- **Shared controller.** `AppGraph.controller` is one `StatusController` per process, on a
  process-wide main scope. The phone's view model and the car session both use it.
  `onSurfaceStarted` and `onSurfaceStopped` are counted, so the timer runs while either surface is
  started (REQ-REFRESH-002).
- **Car app.**
  - `DriveCheckCarAppService` is declared with `androidx.car.app.category.WEATHER`, the
    `automotive_app_desc` template descriptor and `minCarApiLevel` 1.
  - Its host validator allows every host in debug builds (Desktop Head Unit) and Google's allowlist
    in release builds.
  - `DriveCheckCarSession` starts and stops the shared controller with its lifecycle. It derives a
    `CarStatus` from the same `StatusPresenter` the phone uses, on a 10 s tick for staleness, and
    updates the widget and the other surfaces when the snapshot changes.
  - `CarStatusScreen` builds a `PaneTemplate` and invalidates itself when the status changes.
- **Copy.**
  - The onboarding returns to the iOS wording, with CarPlay read as Android Auto.
  - The listing names Android Auto, and the release notes say a sideloaded APK shows no car screen.
  - The copy guard now allows "Android Auto" only while the manifest declares the car service. It
    still refuses CarPlay and Automotive (Android Automotive OS is not shipped).
