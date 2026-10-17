# Drive Check for Android

Drive Check shows a driver in Ukraine whether their region is under an air raid alert, at a
glance, without a map to read or an account to create. This repository is its Android version;
the iOS app is [on the App Store](https://apps.apple.com/app/id6793023910).

**Status:** in development; the first release goes to Google Play.

## Stack

Kotlin · Kotlin Multiplatform (a `shared` module for the domain, the alert provider client and
the refresh rules) · Jetpack Compose · GitHub Actions

## How it is built

Spec first: every feature starts as a specification under `specs/`, then failing tests, then
the code. Behaviour shared with the iOS app keeps the iOS requirement IDs, and every approved
requirement is cited by a test. The work is done by an AI coding agent (Claude Code) under the
owner's review: one pull request per feature, merged after the owner approves it.
