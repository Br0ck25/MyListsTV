# My Lists TV plan (short version)

Decisions (owner approved the direction, the assistant made the calls):
- Name: **My Lists TV**. Sideloaded, not Play Store at first.
- Pages: Home, Movies, Series, Library, Apps (addons are in Settings). Search, profile and settings icons in the top bar.
- Works fully as a guest. Account is optional and never shown as a banner.
- Optional encrypted backup of the whole setup (addons, settings, profiles). The server cannot read it.
- Channel schedules are the same for everyone, computed by My Lists.
- Simple mode by default, Advanced one switch away.
- Player: ExoPlayer only. Auto-pick best stream, automatic fallback to the next source, Sources panel to switch by hand.
- Skip intro, cast lists and "more like this" come after the first release.

Phases (each ends in a gate measured on the slowest test box):
0. Foundations (weeks 1-3): app shell, home role, crash guard, cached home. Gate: Home from cache under 1.5 s.
1. Guest browsing (4-6): default My Lists rows, search, details, stream picker, player. Gate: search to playback, no account.
2. Account and sync (7-9): pairing, signed-in home, change feed, progress sync, encrypted backup. Gate: website edit on TV within 5 s of opening.
3. Channels (10-13): list as channel, schedule API, tuner, prefetch. Gate: channel up/down under 3 s warm.
4. Release (14-17). (Live TV was planned here and removed.)

## Home role (phase 0 spike checklist)
Test on the reference box, per Android version:
1. `adb shell cmd package set-home-activity tv.myliststv.launcher/.MainActivity` (8.1+; on 10+ the package name alone works).
2. Button-mapper app remapping Home.
3. Accessibility-service Home remap (needs user to grant access).
4. Last resort: disable the stock launcher (can break remote buttons such as YouTube).
Record what works per device here.

## Build status (keep this current)
Done: Home (My Lists default rows, Continue Watching, apps row), Movies, Series, Apps, Addons (add by phone page or typing), Search, Settings (toggles, choice and action rows, Simple/Advanced), Details with episodes, stream picker, Media3 player (Sources panel, automatic fallback, expired-link refresh, next episode, skip intro/recap/credits from IntroDB for series, subtitles from files and from subtitle addons, audio, speed, info), Watch Next, profiles with a kids filter, crash guard with safe mode, first-run welcome, sign in with a pairing code (uses My Lists' existing TV pairing flow; swaps the default catalogs for the owner's), encrypted online backup and restore (needs /api/tv-backup deployed, branch `tv-backup-endpoint`), Keystore-wrapped secrets, the My Lists theme (docs/DESIGN.md), sideload updater, tests, CI that tests, builds and publishes the APK.
Not done by the owner's choice: PIN lock.
Not done yet: skip intro for movies (IntroDB has none; TheIntroDB covers few), QR code for sign-in (the code and link are shown as text), watch-history sync from the account.
Not tested: anything on a real TV box (including the Keystore path and Watch Next). First job after sideloading: confirm the home role (see below) and note per-device results here.

- Progress sync (signed in only): part-watched titles go to My Lists `/api/nuvio-tv/progress` (website Continue Watching), finished ones to `/api/nuvio-tv/history`. Needs the addon branch `tv-backup-endpoint` deployed. Home screen setup (Settings): make Home app, Home-button fallback (HomeButtonService, accessibility, sees only which app came forward), phone/computer steps page. Later, optional: on-TV adb pairing over Wireless debugging (needs an adb library).

- UI: Navigation setting: top bar, side bar, or hidden side bar (Left opens). Details has icon buttons and episode cards. Hold OK on a Continue Watching card to remove it.

- Remote buttons: Settings, Remote buttons. A button (colour keys etc.) plus press / double press / hold gives an action (a page, search, settings, switch profile, or open an app). Works through the Home button fallback (accessibility) service. Home goes to the Home page; Home twice quickly opens the profile picker. Rebuilt from the written description of the owner's earlier launcher; none of its code was read.

- Addons moved from a tab into Settings, Addons (AddonsActivity). Settings groups: Addons, General, Playback, Layout, Backup, Performance, Remote buttons, Launcher setup, About. Episode air dates come from the addon's `released` field (Cinemeta has it). Up-next countdown, per-show audio/subtitle memory, Test my sources, screensaver (BackdropDream). SmokeTest `worksWithoutTheMyListsAddon` proves Home, search and details work with only Cinemeta.

- Library: + on a details page saves a title; a Library tab shows them as a page (grid, All / Movies / Series). One list per profile, on the box and in backups (`data/Library.kt`, `ui/LibraryScreen.kt`). Signed in (main profile), it syncs with the account's Watchlist through `/api/nuvio-tv/library` (needs the addon branch deployed): changes are pushed at once, the account is pulled when the launcher opens.

- Episodes: a tick on finished episodes (`data/Watched.kt`; finished = 90% or marked by hand with hold OK), and the main button says Next S1 E4. Bell: new-episode alerts for Library series (`data/Alerts.kt`), checked when the launcher opens, at most every 6 hours, no background service.

- Profiles are separate people: each has its own My Lists sign-in, addon list, Library, history and recovery code (`Profiles.key`, `Profiles.switchTo`). A new profile starts signed out with the default addon. Library, progress and Continue Watching sync with that profile's account; addons and playlists also sync with the account (`data/SetupSync.kt`, `/api/nuvio-tv/setup`; the server stores them encrypted at rest, the TV's own pairing link and the default addon are left out), so a new TV needs only the sign-in. The recovery-code backup remains as an offline copy. The first profile's backup also holds the profile list and box settings.

- Live TV (IPTV playlists, programme guide, channel zapping) was removed at the owner's request. Pages: Home, Movies, Series, Library, Apps.

- Share addons: a profile can use another profile's addon list (Settings, Share addons). It keeps its own sign-in, history and Library; its Addons page is read-only while sharing, the other profile's sign-in link is not shared, and stopping keeps a copy. Dialogs and menus highlight the focused row in brand blue with white text.

- Sign-in shows a QR code (`ui/Qr.kt`, own encoder, no library) and the account name; the account's Continue Watching (website and other TVs, `Progress.pull`) joins the Home row, with next-up episodes shown as "Next up". Moving an app: hold OK, Move, then Left/Right, OK to place. Left from the first card of a page goes to the side bar; Up/Down land on the nearest card of the next row.

- Home rows: Continue Watching (account order, with the title's details on focus) and Airing Next (soonest first, toggle in Settings, Layout). Up/Down land on the card last used in the next row (first card if never visited). The bell is a card list with a Clear button on each notification.

- Launcher polish (added after comparing with Monet and Projectivy): the gear has an Inputs page (HDMI and other TV inputs, `data/SystemLinks.kt`, `ui/SystemPanel.kt`) and a list of Android settings shortcuts. Hold OK on an app: Rename, Change icon (a picture from the TV's files, `data/Pictures.kt`). Settings, Layout: Icon pack (Nova/ADW/Apex format packs installed on the box, `data/IconPacks.kt`) and Wallpaper (shown when no title is highlighted). Picked pictures are copied small into the app's own folder; they are not part of the encrypted backup (the names and pack choice are). Not tested on a real TV: some boxes have no file chooser (the picker then says so), and the Inputs page depends on the TV listing its inputs to apps.
