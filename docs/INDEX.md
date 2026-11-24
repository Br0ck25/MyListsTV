# Where to change what

Start here, then `grep -n <name> docs/MAP.md` for the exact file and line. Rules: CLAUDE.md. Look and feel: docs/DESIGN.md.

| To change | Edit |
|---|---|
| A colour, size, radius, text size, button, dialog look | `ui/Ui.kt` (tokens), `res/values/themes.xml` + `colors.xml` (system dialogs) |
| Poster / wide / app / episode card | `ui/Cards.kt` (`Card`, `CardAdapter`, `RowsList`) |
| Home, Movies, Series rows, hero text, backdrop | `ui/BrowseScreen.kt`, `MainActivity.kt` (`select`, `setBackdrop`) |
| Top bar, side bar, tabs, profile chip, bell | `MainActivity.kt` (`onCreate`, `profileChip`, `dispatchKeyEvent`) |
| Apps tab and the apps row | `ui/AppsScreen.kt`, `AppRepository.kt`, `ui/BrowseScreen.kt` |
| Title details (buttons, cast, episodes, air dates) | `screens/DetailsActivity.kt`, `data/Json.kt` (parse), `data/Models.kt` |
| Source list, choosing and ranking sources | `screens/StreamsActivity.kt`, `data/StreamRank.kt`, `data/StreamResolver.kt` |
| Player, controls, fallback, up next, skip intro | `screens/PlayerActivity.kt`, `ui/PlaySession.kt`, `data/Segments.kt` |
| Search | `ui/SearchScreen.kt`, `data/Catalogs.kt` (`search`) |
| A setting (add, rename, group) | `screens/SettingsActivity.kt` (`rows`), store it in `data/Prefs.kt` |
| Addons list and adding links | `screens/SettingsActivity.kt` (Content & Discovery, Addons), `data/AddonStore.kt`, `data/Links.kt` |
| Catalogs, metadata, Cinemeta details | `data/Catalogs.kt`, `data/Json.kt`, `data/Urls.kt` |
| All network and caching | `data/Http.kt`, `data/ImageLoader.kt` |
| Sign in, backup, progress, Library and addon sync | `data/Pairing.kt`, `data/Backup.kt`, `data/Progress.kt`, `data/SetupSync.kt`, `data/Library.kt`, `data/Secure.kt` |
| Library (+ button, Library page) | `data/Library.kt`, `ui/LibraryScreen.kt`, `screens/DetailsActivity.kt` |
| Watched ticks, new-episode alerts | `data/Watched.kt`, `data/Alerts.kt`, `screens/DetailsActivity.kt`, `MainActivity.kt` (`showNotices`) |
| Share addons between profiles | `data/Profiles.kt` (`addonSource`), `data/AddonStore.kt` (`saved`), `screens/SettingsActivity.kt` (`shareMenu`) |
| Profiles, kids filter, watch history | `data/Profiles.kt`, `data/WatchStore.kt` |
| Remote button mapper, Home button | `data/ButtonMap.kt`, `services/HomeButtonService.kt`, `data/HomeSetup.kt` |
| Gear panel: Inputs (HDMI), Android settings shortcuts | `ui/SystemPanel.kt`, `data/SystemLinks.kt`, `MainActivity.kt` (`showSettingsPanel`) |
| Wallpaper, picking a picture from the device | `data/Pictures.kt`, `ui/PicturePicker.kt`, `MainActivity.kt` (`idleBackdrop`), `screens/SettingsActivity.kt` (`wallpaperMenu`) |
| Icon packs, a picked icon for one app, rename app | `data/IconPacks.kt`, `AppRepository.kt` (`look`), `data/Pictures.kt`, `MainActivity.kt` (`appMenu`, `iconMenu`), `data/Prefs.kt` (`appNames`, `iconPack`) |
| Notifications bell | `services/NotificationsService.kt` (`Notices`), `MainActivity.kt` (`showNotices`) |
| Screensaver | `services/BackdropDream.kt` |
| Watch Next row on the system home | `data/WatchNext.kt` |
| First-run setup screens (Home app, Home button, notifications, addons) | `screens/SetupActivity.kt`, `data/HomeSetup.kt`, `MainActivity.kt` (`welcome`) |
| Phone page (paste links, setup steps, send a picture) | `data/PhoneServer.kt`, `ui/PhonePicture.kt` |
| Crash safe mode | `data/CrashGuard.kt` |
| Updates | `data/Updater.kt` |

Tests are in `app/src/test`: `SmokeTest` (every screen starts), `DesignGuardTest` (no one-off colours or sizes), `SizeGuardTest` (2,000 lines), `ManifestTest`, `ShotTest` (draws PNGs when `SHOTS_DIR` is set), `data/*` (logic).
After adding, moving or renaming code run `python3 tools/gen_map.py` and commit `docs/MAP.md`; CI fails if it is stale.
