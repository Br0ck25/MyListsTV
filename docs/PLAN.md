# My Lists TV plan (short version)

Decisions (owner approved the direction, the assistant made the calls):
- Name: **My Lists TV**. Sideloaded, not Play Store at first.
- Six tabs: Home, Movies, Series, Live, Apps, Addons. Search, profile and settings icons in the top bar.
- Works fully as a guest. Account is optional and never shown as a banner.
- Optional encrypted backup of the whole setup (addons, playlists, settings, profiles). The server cannot read it.
- Channel schedules are the same for everyone, computed by My Lists.
- Simple mode by default, Advanced one switch away.
- Player: ExoPlayer only. Auto-pick best stream, automatic fallback to the next source, Sources panel to switch by hand.
- Skip intro, cast lists and "more like this" come after the first release.

Phases (each ends in a gate measured on the slowest test box):
0. Foundations (weeks 1-3): app shell, home role, crash guard, cached home. Gate: Home from cache under 1.5 s.
1. Guest browsing (4-6): default My Lists rows, search, details, stream picker, player. Gate: search to playback, no account.
2. Account and sync (7-9): pairing, signed-in home, change feed, progress sync, encrypted backup. Gate: website edit on TV within 5 s of opening.
3. Channels (10-13): list as channel, schedule API, tuner, prefetch. Gate: channel up/down under 3 s warm.
4. Live TV and release (14-17): M3U/Xtream import, guide, favorites. Gate: big playlist imports without memory spike.

## Home role (phase 0 spike checklist)
Test on the reference box, per Android version:
1. `adb shell cmd package set-home-activity tv.myliststv.launcher/.MainActivity` (8.1+; on 10+ the package name alone works).
2. Button-mapper app remapping Home.
3. Accessibility-service Home remap (needs user to grant access).
4. Last resort: disable the stock launcher (can break remote buttons such as YouTube).
Record what works per device here.

## Build status (keep this current)
Done: Home (My Lists default rows, Continue watching, apps row), Movies, Series, Live (M3U with XMLTV guide), Apps, Addons (add by phone page or typing), Search, Settings (toggles, choice and action rows, Simple/Advanced), Details with episodes, stream picker, Media3 player (Sources panel, automatic fallback, expired-link refresh, next episode, skip intro/recap/credits from IntroDB for series, subtitles from files and from subtitle addons, audio, speed, info), Watch Next, profiles with a kids filter, crash guard with safe mode, first-run welcome, sign in with a pairing code (uses My Lists' existing TV pairing flow; swaps the default catalogs for the owner's), encrypted online backup and restore (needs /api/tv-backup deployed, branch `tv-backup-endpoint`), Keystore-wrapped secrets, the My Lists theme (docs/DESIGN.md), sideload updater, tests, CI that tests, builds and publishes the APK.
Not done by the owner's choice: PIN lock.
Not done yet: skip intro for movies (IntroDB has none; TheIntroDB covers few), QR code for sign-in (the code and link are shown as text), watch-history sync from the account.
Not tested: anything on a real TV box (including the Keystore path and Watch Next). First job after sideloading: confirm the home role (see below) and note per-device results here.

- Progress sync (signed in only): part-watched titles go to My Lists `/api/nuvio-tv/progress` (website Continue Watching), finished ones to `/api/nuvio-tv/history`. Needs the addon branch `tv-backup-endpoint` deployed. Home screen setup (Settings): make Home app, Home-button fallback (HomeButtonService, accessibility, sees only which app came forward), phone/computer steps page. Later, optional: on-TV adb pairing over Wireless debugging (needs an adb library).

- UI: Live TV is a programme guide (groups, time axis, channel rows). Navigation setting: top bar, side bar, or hidden side bar (Left opens). Details has icon buttons and episode cards. Hold OK on a Continue watching card to remove it.
