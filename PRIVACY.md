# Summit privacy policy

_Last updated: 17 November 2026_

Summit is a home screen for Android TV. It has no account, no ads, no analytics and no crash reporting, and the
developer does not receive any data from it.

## What stays on your TV
- **App usage** (for the Continue row) is read from Android's usage access and never leaves the TV.
- **Pictures** you choose as a wallpaper are read from the TV's storage and never leave the TV.
- **Settings**, favourites and cached weather are stored only on the TV and are removed when Summit is uninstalled.

## What Summit sends over the internet
Summit only contacts these services, and only for the features named:

| Service | What it receives | Why |
|---|---|---|
| [ipapi.co](https://ipapi.co/privacy/), falling back to [ipwho.is](https://ipwho.is/) | The TV's public IP address (as any web request does) | Find the approximate city for weather, at most once a day, only while weather follows the current location |
| [Open-Meteo](https://open-meteo.com/en/terms#privacy) | The latitude and longitude of the weather city, or the city name you type in search | Weather forecast and city search |
| GitHub (`raw.githubusercontent.com`, `api.github.com`) | The TV's public IP address | Community wallpaper list and downloads, and the latest version number shown in Settings |

No identifiers, app lists or personal information are sent. Choosing a city in **Settings › Weather › Location**
stops the IP lookup.

## Children
Summit does not knowingly collect any personal information from anyone, including children.

## Changes
Changes to this policy are published in this file, with the date above updated.

## Contact
Open an issue at https://github.com/exendahal/summit_android_tv_launcher/issues.
