# Google Play release

Listing text and graphics live in `fastlane/metadata/android/en-US/` (git-ignored, kept locally) (the layout `fastlane supply` uploads, or copy
them by hand). The upload file is `summit-vX.Y.Z.aab` from the GitHub release.

That bundle is the `play` build type (`./gradlew bundleProdDataPlay`). It differs from the GitHub APK only where Play
policy requires: no photo permission (Wallpaper › My picture uses the system picker, and is hidden on TVs without
one) and no GitHub update check.

| Play Console field | Source |
|---|---|
| App name (30) | `title.txt` |
| Short description (80) | `short_description.txt` |
| Full description (4000) | `full_description.txt` |
| Release notes | `changelogs/default.txt` |
| App icon 512×512 | `images/icon.png` |
| Feature graphic 1024×500 | `images/featureGraphic.png` |
| Android TV banner 1280×720 | `images/tvBanner.png` |
| Android TV screenshots 1920×1080 | `images/tvScreenshots/` |
| Privacy policy URL | https://exendahal.github.io/summit_android_tv_launcher/privacy-policy/ (published by `.github/workflows/pages.yml`) |

## App content answers
- **Category:** Tools (or Personalization). **Ads:** No. **Target audience:** 13+ (not designed for children).
- **Content rating questionnaire:** utility app, no user-generated content, no sharing, no purchases.
- **Data safety:** encrypted in transit: Yes (all requests are HTTPS); deletion request: not applicable. Declare
  *Location › Approximate location*: collected, processed ephemerally, optional, for *App functionality*
  (the IP-based city and the coordinates sent to Open-Meteo). Usage stats and photos stay on the device: not collected.
- **Advertising ID:** No.

## Device catalogue / Android TV
- Opt in to **Android TV** under *Advanced settings › Form factors*; it is reviewed separately against the
  [TV app quality guidelines](https://developer.android.com/docs/quality-guidelines/tv-app-quality).
- `leanback` is required in the manifest, so phones and tablets are excluded automatically.

## Signing
Enrol in Play App Signing and use the CI keystore (`ANDROID_KEYSTORE_*` secrets) as the **upload key**. Play then
re-signs installs with Google's key, so a GitHub APK cannot update a Play install (and vice versa).
