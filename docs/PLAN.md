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
Done: Home (My Lists default rows, Continue watching, apps row), Movies, Series, Live (M3U), Apps, Addons (add by phone page or typing), Search, Settings (Simple/Advanced), Details with episodes, stream picker, Media3 player (Sources panel, automatic fallback, expired-link refresh, next episode, subtitles/audio/speed/info), sideload updater, 25 unit and smoke tests, CI that tests, builds and publishes the APK.
Not done yet: account pairing and encrypted backup (needs My Lists Addon endpoints), Watch Next channel, profiles and PIN, first-run wizard, crash guard, EPG for Live, subtitles from addons, skip intro.
Not tested: anything on a real TV box. First job after sideloading: confirm the home role (see below) and note per-device results here.
