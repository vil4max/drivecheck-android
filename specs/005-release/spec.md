# Feature specification: release to Google Play

Feature: `005-release` · Created: 2026-10-17
Status: approved (owner, 2026-10-17, through the orchestrator: «заканчивай проект и в маркет»; merge needs his word)
Input: the iOS release material in `regional-check` (`docs/operations/app-store-copy.md`,
`docs/privacy-policy.html`, the app icon, `release/screenshots/asc/`)

## Summary

The first build goes to a Google Play closed test. The closed test runs at least 14 days with at
least 12 testers, and must come before production access.

This slice adds:
- the app icon;
- a release build signed with an upload key kept outside the repository (Play App Signing holds the app signing key);
- the store listing in en, uk and ru;
- the screenshots;
- the privacy policy;
- the Data safety and content rating answers;
- the closed-test plan.

The owner does every Play Console step himself; the answers and assets are in [release-checklist.md](release-checklist.md).

## Android Auto

Android Auto lists only apps in its supported categories. None of them fits an air-raid alert app:
- the weather category requires weather content (quality guideline WE-1);
- PC-1 forbids features outside the car app types.

The decision is the owner's and is pending. Until he decides:
- this slice ships no Android Auto screen;
- T018 of spec 002 is applied: the onboarding and the listing do not promise Android Auto.

## Rules the listing follows

These come from the iOS copy rules:
- Never "alert monitor": the app shows regional alert status, it does not monitor.
- The map is a reference image from the provider, never navigation.
- The signal and the map are free on every surface.
- Alert data is informational only; follow official instructions.
- No promise of a surface this build does not ship.

## Out of scope

- Pro, the cosmetic purchase.
- Production rollout: it follows the closed test, on the owner's word.
