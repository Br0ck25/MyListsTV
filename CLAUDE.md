# CLAUDE.md

**My Lists TV**: a lightweight Android TV launcher that uses Stremio-style addons, with My Lists Addon (github.com/Br0ck25/My-Lists-Addon) as its default catalog source. The owner is not a coder: run every command, test and commit yourself, and explain plainly.

The full plan lives in the "My Lists TV: Build Plan" doc the owner shares; `docs/PLAN.md` is the short version that must stay in sync with it.

## Hard rules (1 GB RAM target)
1. Plain Android Views and RecyclerView on Home. **No Compose, no WebView, no AppCompat** on Home. Framework theme only.
2. Never read or copy code from GPL-3.0 reference launchers (FLauncher, Arc Launcher, LTvLauncher, NuvioTV). Ideas only.
3. ExoPlayer (Media3) only for playback. No libmpv, no torrent engine, no on-device P2P.
4. Ship `armeabi-v7a` and `arm64-v8a`. Never assume 64-bit.
5. No analytics, no tracking, no network call that isn't for catalogs, streams, subtitles, updates or sync.
6. Debrid keys and IPTV logins are secrets: Keystore-backed storage, never logged, never sent unencrypted. Backups are encrypted before leaving the device.
7. Images decode at card size. Caches are byte-bounded. Never block the main thread with I/O.
8. Every phase ends in a gate measured on the reference 1 GB box (see docs/PLAN.md).
9. **Ask only about product decisions.** Decide technical choices yourself and say what you chose.
10. Small, root-cause changes. Don't refactor unrelated code. Never skip or loosen a test to get green.
11. Don't commit secrets, keystores or `local.properties`.

## Working rules (added at the owner's request, from the Karpathy-style guidelines)
These bias toward care over speed. For trivial work, use judgment. Where they meet the owner's rules above, ask only about product decisions and decide technical choices yourself.

1. **Think before coding.** State assumptions. If two readings would change what the user sees, say so or ask. If a simpler way exists, say so and push back. If something is unclear, stop and name what is confusing.
2. **Simplicity first.** The minimum code that solves the problem. No features that were not asked for, no abstraction for single-use code, no configurability nobody requested, no error handling for impossible cases. If 200 lines could be 50, rewrite it. Ask: would a senior engineer call this overcomplicated?
3. **Surgical changes.** Touch only what the task needs. Do not "improve" nearby code, comments or formatting, and do not refactor what is not broken. Match the existing style. Mention unrelated dead code instead of deleting it. Remove only what your own change made unused. Every changed line should trace back to the request.
4. **Goal-driven execution.** Turn each task into a check you can run: "add validation" becomes "write tests for bad input, then make them pass"; "fix the bug" becomes "write a test that reproduces it, then make it pass"; "refactor" means tests pass before and after. For multi-step work, state a short plan with a check after each step. Loop until the checks pass.

## Keep the code small (owner's rule)
- Fewest lines that do the job. Before writing anything, look for an existing helper: `ui/Ui.kt` (views, colors, focus), `ui/Cards.kt` (`CardAdapter` for any strip or grid), `data/Http.kt` (all network), `data/Prefs.kt` (all settings), `data/AddonStore.kt` and `data/Catalogs.kt` (addons and catalogs).
- One implementation per job. Add to a shared helper instead of copying code into a screen.
- No new library when the platform already does it. Delete code a change made unused.

## Build
- CI builds the APK on every push (`.github/workflows/build.yml`), downloadable from the run's artifacts.
- Locally: `./gradlew :app:assembleDebug` (needs the Android SDK).

## Changes to My Lists Addon
Allowed when the plan needs them (pairing, backup, home.json, schedule). Follow that repo's `CLAUDE.md` exactly (edit `src/` files, run `python build.py`, run `bash verify.sh -q`).
