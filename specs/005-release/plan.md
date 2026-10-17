# Implementation plan: release to Google Play

Feature: `005-release` · Spec: [spec.md](spec.md) · Tasks: [tasks.md](tasks.md)

## Design

- **Icon.** An adaptive icon from the iOS 1024 px art. It is inset to the 72 dp square that every
  launcher mask shows, on the art's sky blue, so no mask crops it. The Play icon is the same art at
  512 px.
- **Signing.**
  - A `upload` signing config is created only when the owner's Gradle properties name a keystore,
    so CI and other machines build an unsigned bundle.
  - No secret and no key enters the repository.
- **Listing.**
  - [play-listing.md](play-listing.md) holds the en, uk and ru texts.
  - `scripts/listing-lengths.py` checks them against Play's limits; it runs in the gate through the
    scripts' unit tests.
- **Screenshots.** `PlayScreenshotTest` renders the store set in three locales at 1080 × 2160 (Play's
  2:1 limit). Each screen's region agrees with the map fixture under it.
- **Privacy policy.** `docs/privacy-policy.html`, for GitHub Pages. It states only what the code does.
- **Console answers.** [release-checklist.md](release-checklist.md): Data safety, content rating,
  declarations and the closed-test plan.

## Not done here

- No new dependency.
- No R8 shrinking for 1.0: the AAB is 8.5 MB and shrinking needs its own device check.
