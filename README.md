# Summit

Android TV home-screen launcher: one-press live TV, weather, scores and recent/favourite apps, in 40+ languages,
with Nepal extras (Bikram Sambat date, Devanagari digits) where the TV is in Nepal. Product spec (written under the
earlier name Himal): `Himal_Launcher_PRD.md`.

Package ID: `np.exendahal.summitlauncher`. Builds from before the rename used `np.himal.launcher`; Android treats that
as a different app, so its settings do not carry over.

## Requirements
- JDK 17, Android SDK platform 34, Gradle 8.7 (wrapper included), AGP 8.5.2.
- An Android TV device or emulator, API 26+.

## Build variants
| Variant | Data |
|---|---|
| `fakeData` | Appendix B demo weather and scores, no network |
| `prodData` | Live weather (Open-Meteo) and live scores (ESPN public scoreboard) |

```sh
./gradlew assembleProdDataRelease        # app/build/outputs/apk/prodData/release/
./gradlew testFakeDataDebugUnitTest      # BS calendar, units, score ordering
```
On Windows, if the unit tests fail with `Could not find or load main class Files\...`, your `PATH` contains a stray
`"` character. Remove it or run with a cleaned `PATH`.

Release builds are signed with the debug key so you can sideload them. Before publishing to Play, add a real
`signingConfig`.

## Run on a TV over ADB
```sh
adb connect 192.168.101.14
adb install -r app/build/outputs/apk/prodData/release/app-prodData-release.apk
adb shell appops set np.exendahal.summitlauncher GET_USAGE_STATS allow   # Continue row (recent apps)
adb shell am start -n np.exendahal.summitlauncher/.HomeActivity
```

### Make it the default home screen
The stock Google TV launcher (`com.google.android.tvlauncher`) is a system app with a higher HOME priority, so
Android ignores any other home choice. Disable it for the user, then set Summit:
```sh
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell cmd package set-home-activity np.exendahal.summitlauncher/.HomeActivity
```
To undo this and restore the stock launcher:
```sh
adb shell pm enable com.google.android.tvlauncher
adb shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
```

### First run
On a fresh install (or **Settings › Run first-time setup**), onboarding asks:
1. **Where do you watch TV?** First choose **TV input** or **App**, then pick the HDMI/AV/antenna input or the live-TV app.
   Only physical inputs are listed; apps' preview inputs are filtered out.
2. **Scores:** which leagues to show on Home.
3. **Language:** English, Nepali and 41 other languages. Only English and Nepali are translated so far; the others use
   English text with local date formats.
4. **Look:** Auto, Dark or Light.

### Weather location
By default weather follows the TV's **current location**, found once a day from its public IP address (ipapi.co,
falling back to ipwho.is). The detection is city-level and stops being used once you search for a city. To change the city,
open the weather chip on Home (or **Settings › Weather › Location**). **Change city** offers *Use current location*
or a search box that finds any city through Open-Meteo geocoding.

### Region (Nepal-only options)
Summit treats the TV as in Nepal when the **system** region is NP **or** the time zone is Kathmandu
(`Asia/Kathmandu` / `Asia/Katmandu`). Many boxes sold in Nepal run en-US, so the time zone is the main signal.
Outside Nepal the **Calendar** and **Devanagari digits** options are hidden and only the Gregorian date is shown.
Choosing Nepali as the app language still uses Devanagari digits anywhere.

### Scores on Home
Home only shows matches that are live, start within the next hour, or finished within the last hour. If none
qualify, the scores row is hidden.

`prodData` reads ESPN's public scoreboard (`site.api.espn.com`, unofficial, no key) for Premier League, LaLiga,
Champions League, Serie A, Bundesliga, Ligue 1, NBA, NFL, NHL and MLB. It refreshes every 60 s while a match is
live and every 15 min otherwise. Cricket (NPL, IPL, Big Bash, internationals), EuroLeague, tennis, F1, MotoGP and
golf have no source yet, so they show nothing. `fakeData` shows the PRD's demo matches. Google is not used:
it has no scores API, and scraping its result pages is against its terms and gets blocked.

### Use the ambient screensaver
Turn on **Settings › Screensaver › Use as screensaver** in Summit; turning it off restores the previous screensaver.
Android only lets an app change the system screensaver after a one-time grant over ADB:
```sh
adb shell pm grant np.exendahal.summitlauncher android.permission.WRITE_SECURE_SETTINGS
```
Without the grant, the option opens the TV's own screensaver settings, or explains the grant on TVs that have none
(e.g. Mi TV, which replaces that screen with a photo-album picker).

## Architecture
A single activity with one screen per feature. Everything is in `app/src/main/java/np/exendahal/summitlauncher/`:

| Class | Role |
|---|---|
| `HomeActivity` | HOME activity. Hosts Home/Apps/Settings fragments (fade-through), wallpaper layer, Home/Search/Settings keys, Back hierarchy (PRD 7.2), minute tick, package-change receiver |
| `HomeFragment` | Header, clock, BS/AD dates, weather chip and summary, score chips, Continue and Favourites rows, move mode |
| `AppsFragment` | All TV apps in one alphabetical 6-column grid with the hold-OK/Menu app menu. No category filters: apps declare categories unreliably (VLC says audio). |
| `SetupFragment` | First-run onboarding: TV source (input or app), scores, language, look |
| `SettingsFragment` | Android TV preference-style drill-down: categories → options (title + current value) → values (radio list, wallpaper thumbnails); Back goes up a level; live Home preview beside the list |
| `AmbientDreamService` | Screensaver with burn-in shift and night dim |
| `FocusMemoryRecyclerView` | Row focus memory and keyline scrolling (PRD 7.3) |
| `TileAdapter`, `Ui` | Tiles, focus visuals (scale, ring, corner morph), cookie badge, text scaling, digits |
| `ThemeController` | Dark/light/auto, seed overlays (5 wallpaper seeds + 9 custom colours, × 2 modes), high contrast, wallpaper + tint + dim, picture → nearest colour |
| `Prefs` | Settings (keys and defaults from PRD 9) |
| `CustomWallpaper` | Theme › Wallpaper › My picture: lists the TV's pictures (MediaStore), saves the pick scaled and upright in app storage |
| `AppsRepo`, `TvSource` | Installed apps, favourites, recents (UsageStats), Watch TV input/app launch, voice search |
| `Weather`, `Scores`, `BikramSambat` | Open-Meteo client, cache and summary; score model and ordering; BS conversion |

The BS month table in `res/raw/bs_calendar.json` (BS 2000–2090) comes from the MIT-licensed
`nepali-date-converter` 3.4.2. It is verified against official new-year dates and Kartik 1 2083 = 18 Oct 2026.

## Deliberate departures from the PRD tech stack
To keep the app light on 1 GB boxes and the code small, the PRD stack was simplified:

| PRD | Here | Upgrade when |
|---|---|---|
| Hilt, ViewModel/LiveData | Plain singletons, fragments bind directly | Screens get tested in isolation or grow state |
| Room | SharedPreferences (favourites list, weather JSON cache) | Data outgrows a few KB |
| Retrofit/OkHttp/Moshi | `HttpURLConnection` + `org.json` | More than one API |
| WorkManager refresh | Refresh on Home resume when the cache is over 30 min old | Background freshness matters |
| Bundled Roboto Flex / Noto fonts | System fonts (Android ships Noto Devanagari) | Variable-width typography is required |
| Settings stored as typed values | All values stored as stable strings, read by typed getters | Never needed |
