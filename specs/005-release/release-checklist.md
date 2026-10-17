# Release checklist: Google Play

The owner does every Play Console step. This file holds the answers and assets those steps need.
Each answer was checked against the code on 2026-10-17. An answer that rests on an assumption
says so.

## Build

- `./gradlew :androidApp:bundleRelease` builds `androidApp/build/outputs/bundle/release/androidApp-release.aab`.
- It is signed with the upload key when these four properties are set in `~/.gradle/gradle.properties`:
  - `drivecheck.upload.storeFile`
  - `drivecheck.upload.storePassword`
  - `drivecheck.upload.keyAlias`
  - `drivecheck.upload.keyPassword`
- The key file stays outside the repository; `.gitignore` refuses `*.jks` and `*.keystore`.
- Play App Signing holds the app signing key, and Play Console offers it when the first bundle is uploaded.
- Version: `versionName` 1.0.0, `versionCode` 1. Every later upload raises `versionCode`.
- Target SDK: 37. Min SDK: 26.

## Assets

| Asset | File |
|---|---|
| App icon 512 × 512 | `release/play/icon-512.png` |
| Phone screenshots 1080 × 2160, en, uk, ru | `release/play/screenshots/<locale>/01…05.png` (from `PlayScreenshotTest`) |
| Feature graphic 1024 × 500 | not made yet: required before production, not for the closed test |
| Listing texts | [play-listing.md](play-listing.md) |
| Privacy policy | `docs/privacy-policy.html`, served by GitHub Pages from `main` `/docs` |

## Data safety

**Network calls.** The app sends only:
- the alert feed request and the map image request to `ubilling.net.ua`, with no personal data;
- Android's geocoder lookup.

**Location.** The app reads location only while it is on screen. The platform `Geocoder` turns it
into a region and may send the coordinates to the device's geocoding provider. The app's own code
never sends location anywhere.

Assumption, to be checked by the owner against Play's definition of collection:
- The geocoder lookup counts as collection, because the coordinates leave the device. The cautious
  answer below declares it.
- If Play treats an OS service as not the app's collection, the answer can be "No data collected".

| Question | Answer |
|---|---|
| Does the app collect or share any required user data types? | Yes |
| Is all collected data encrypted in transit? | Yes (HTTPS) |
| Do you provide a way to request deletion? | Not applicable: nothing is stored off the device |
| Location: approximate | Collected, not shared; processed ephemerally; optional (the app falls back to Kyiv); purpose: app functionality |
| Location: precise | Collected, not shared; processed ephemerally; optional; purpose: app functionality |
| Every other data type | Not collected |

## Content rating (IARC questionnaire)

- Category: Utility, productivity, communication or other. The app has no user-generated content,
  no chat, no purchases and no ads.
- It names air raid alerts (news of a real-world threat) without depicting violence. Answer "No"
  to every violence, fear, sexuality, language, controlled-substance and gambling question.
- Location: the app uses the device location, but does not share it with other users.

## App content declarations

| Declaration | Answer |
|---|---|
| Ads | No ads |
| Target audience | 18 and over (drivers); not designed for children |
| News app | No |
| Government app | No: the app is not made by or on behalf of a government, and the listing says the data is informational |
| Health, financial features | None |
| Foreground service | None |
| Exact alarms | None: the timed redraw uses an inexact `AlarmManager.set` |
| Android Auto | Add the Android Auto form factor (Release, Advanced settings, Form factors) and accept its terms; the car app is in the weather category and goes through the manual car review (spec 004) |
| Notification permission | Asked only when the driver turns on the alert notification (Android 13+) |

## Closed test plan

- **Track:** closed testing, one email list of at least 12 testers.
- **Duration:** at least 14 consecutive days with at least 12 opted-in testers. Play requires this
  before a new personal developer account can apply for production.
- **Start:** with the first usable build. Every fix during the test is a new `versionCode` on the same track.
- **Ask testers to:**
  - install from the opt-in link;
  - open the app daily;
  - add the widget and the tile;
  - turn on the alert notification;
  - report through the Play test feedback or the repository's issues.
- **Exit:** 14 days, no crash or ANR in Android vitals, the device runtime checks of spec 002 T017
  and spec 003 SC-003 done on at least one tester's phone. Then the owner applies for production.
