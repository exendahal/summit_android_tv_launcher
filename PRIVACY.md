# Privacy Policy

**App:** Summit (Android TV home screen), package `np.exendahal.summitlauncher`
**Effective date:** 17 November 2026

This Privacy Policy explains what information the Summit app ("Summit", "the app", "we") handles, how it is used,
and the choices you have. By using Summit you agree to this policy.

## 1. Information we collect
Summit has no account, no advertising, no analytics and no crash reporting. **The developer does not collect, store
or receive any personal information from the app.**

## 2. Information processed on your device
The following stays on your TV and is never sent to the developer or any third party:

- **App usage**, read through Android's usage access (only if you grant it), to show the apps you used last in the
  Continue row.
- **Pictures** you choose as a wallpaper. The chosen picture is copied into the app's private storage.
- **Settings**, favourite apps and cached weather.

## 3. Information sent to third-party services
To provide some features, Summit contacts the services below directly from your TV. Like any internet request, each
one receives your TV's public IP address. Their own privacy policies apply.

| Service | Data sent | Purpose |
|---|---|---|
| [ipapi.co](https://ipapi.co/privacy/), falling back to [ipwho.is](https://ipwho.is/) | Public IP address | Find your approximate city for weather, at most once a day, only while weather uses the current location |
| [Open-Meteo](https://open-meteo.com/en/terms#privacy) | Latitude and longitude of the weather city, or the city name you search for | Weather forecast and city search |
| [GitHub](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement) | Public IP address | Community wallpaper list and downloads; the latest version number (GitHub version only) |

No device identifiers, installed-app lists, usage data or pictures are sent. All requests use encrypted HTTPS.

## 4. Location
Summit does not use GPS or the Android location permission. Approximate location is derived from your IP address
only for weather. You can stop this at any time by choosing a city in **Settings › Weather › Location**.

## 5. Permissions
| Permission | Why |
|---|---|
| Internet, network state | Weather, wallpapers and the version check |
| Usage access (optional) | The Continue row on Home |
| Read photos (GitHub version only) | List your pictures to choose a wallpaper |
| Read TV listings | Find your TV inputs and live-TV channels for Watch TV |
| Run at start-up | Open Watch TV when the TV starts, if you turn that on |
| Change secure settings (only if you grant it over adb) | Set Summit as the screensaver |

## 6. Data retention and deletion
All data Summit keeps is stored on your TV. Clearing the app's data or uninstalling Summit deletes it. The developer
holds no data about you, so there is nothing to request or delete from us.

## 7. Children's privacy
Summit is not directed at children and does not knowingly collect personal information from anyone, including
children under 13.

## 8. Security
Summit stores its data in Android's private app storage, which other apps cannot read, and uses HTTPS for every
network request.

## 9. Changes to this policy
We may update this policy. Changes are published at this address with a new effective date.

## 10. Contact
Questions about this policy: open an issue at
[github.com/exendahal/summit_android_tv_launcher/issues](https://github.com/exendahal/summit_android_tv_launcher/issues).
