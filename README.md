# My Lists TV

A fast, private Android TV launcher built for low-end boxes (1 GB RAM). It works fully without an
account, uses Stremio-style addons, and uses [My Lists Addon](https://github.com/Br0ck25/My-Lists-Addon)
for default catalogs, search metadata, channels and optional encrypted backup.

**Status:** first working build. Home, Movies, Series, Live (M3U), Apps, Addons, Search, Settings, details, stream picker, and a player with automatic and manual source switching.

## Install (sideload)
1. Download `MyListsTV.apk` from the [latest release](https://github.com/Br0ck25/MyListsTV/releases) (a new one is published on every push to `main`).
2. Put it on your TV: with the Downloader app, a USB stick, or `adb install MyListsTV.apk`.
3. Open **My Lists TV** from your apps. Later updates: Settings, Check for updates.

## First run
The guest home shows default My Lists catalogs. To play video you need a **stream addon** (for example one set up with your debrid service): open the Addons tab and paste its link, or open the address shown there on your phone and paste it. IPTV: paste an M3U playlist link the same way, then use the Live tab.

## Make it the home screen
See `docs/PLAN.md`, "Home role". The command that works on many boxes:

```
adb shell cmd package set-home-activity tv.myliststv.launcher/.MainActivity
```
It needs Android 8.1 or newer. Fire OS restricts it, so Fire TV needs a launcher-manager tool.

## Build
```
./gradlew :app:assembleDebug
```

## License
To be decided by the owner. No third-party launcher code is used.
