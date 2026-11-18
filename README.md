# My Lists TV

A fast, private Android TV launcher built for low-end boxes (1 GB RAM). It works fully without an
account, uses Stremio-style addons, and uses [My Lists Addon](https://github.com/Br0ck25/My-Lists-Addon)
for default catalogs, search metadata, channels and optional encrypted backup.

**Status:** phase 0 spike (can it become the home screen and list apps).

## Install (sideload)
Download the APK from the latest GitHub Actions run (Artifacts) or Releases, then install it on your box.

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
