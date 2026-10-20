# Drive Check for Android

Drive Check shows a driver in Ukraine whether their region is under an air raid alert, at a
glance, without a map to read or an account to create. This repository is its Android version;
the iOS app is [on the App Store](https://apps.apple.com/app/id6793023910).

**Status:** 1.0.0 is out as an APK on GitHub; Google Play follows.

## Download

Get `DriveCheck-1.0.0.apk` from the [latest release](https://github.com/vil4max/drivecheck-android/releases/latest)
and install it on Android 8.0 or later. Android asks once to allow installs from the app you
open the file with (the browser or the file manager). If a later Google Play build will not
install over it, uninstall the APK first.

What 1.0 does:
- shows the current region's status and when it was checked;
- the region follows your location;
- alerts in neighbouring regions;
- the alert map;
- a home-screen widget and a quick-settings tile;
- a lock-screen notification while your region is under alert, once the app or the widget sees it;
- an Android Auto screen with the status, the region, nearby alerts and Refresh.

Alert data is informational only; it is not a push alert service. Follow official instructions.
The Android Auto screen appears in a car only when the app is installed from Google Play: Android
Auto's "unknown sources" option does not cover car apps like this one ([spec 004](specs/004-android-auto/spec.md)).
[Privacy policy](https://vil4max.com/drivecheck-android/privacy-policy).

## Stack

Kotlin · Kotlin Multiplatform (a `shared` module for the domain, the alert provider client and
the refresh rules) · Jetpack Compose · GitHub Actions

## How it is built

Spec first: every feature starts as a specification under `specs/`, then failing tests, then
the code. Behaviour shared with the iOS app keeps the iOS requirement IDs, and every approved
requirement is cited by a test. The work is done by an AI coding agent (Claude Code) under the
owner's review: one pull request per feature, merged after the owner approves it.
