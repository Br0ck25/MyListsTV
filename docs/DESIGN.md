# DESIGN.md — My Lists TV look and menu rules

> Audience: anyone (human or AI) adding or changing a screen. Follow every rule. Tokens live in `ui/Ui.kt`; if a token is missing, add it there **and** here.
> Decision (owner asked, assistant decided): **use the My Lists palette** from the website's design system (`My-Lists-Addon/docs/DESIGN_SYSTEM.md`, dark values), with larger rounded shapes in the spirit of Projectivy and Material "Monet" panels. The site and the TV then look like one product, and every colour pair below was already checked for contrast (5:1 or better against white text).

## 1. Look in one paragraph
True-black canvas, soft dark-grey rounded panels, white text, one highlight colour (blue unless the owner picks another in Settings, Layout, Highlight colour). Everything focusable is rounded; buttons, tabs and toggles are full pills. Focus is always the highlight-colour ring, on a faint fill of the same colour for rows and pills. A focused card also rises: it grows 15% above its neighbours and casts a glow in the highlight colour (no growing in lite mode), and the screen behind Home takes a soft tint of the focused title's backdrop. The highlight colour means "on, active or primary" and is drawn as an outline for a selection. No gradients except the dark fade behind text on artwork. No second accent colour.

## 2. Tokens
All sizes are in design units for a 960-wide screen. `Ui.dp()` scales them by the real screen width, so the same numbers look right on every TV.

### 2.1 Colours (dark only; a TV has no light theme)
| Token (`Ui.`) | Value | Use |
|---|---|---|
| `BG` | `#000000` | Screen background |
| `CARD` | `#1C1C1E` | Rows, panels, tiles, inputs |
| `ELEVATED` | `#2C2C2E` | Dialogs, panels on top of panels |
| `TEXT` | `#FFFFFF` | Titles and body |
| `TEXT2` | `#EBEBF5` | Secondary body, button labels |
| `DIM` | `#AEAEB2` | Hints, meta text, placeholders |
| `BRAND` | `#0A84FF` (default) | The highlight colour: the focus ring, active state, progress, toggle on, meta accents. Follows `Ui.ACCENTS` and `Prefs.accent` |
| `BRAND_FILL` | `#0A64D8` (default) | Fills that carry white text (≥ 5:1); each accent has its own |
| `GOOD` | `#30D158` | Success text, "Cached" |
| `WARN` | `#FF9F0A` | Warnings |
| `BAD` | `#FF453A` | Errors, destructive actions |
| `BRAND_SOFT` | `BRAND` at 17% | Faint fill inside the ring of whatever has focus (also `FOCUS_FILL`) |
| `LINE` | `#24FFFFFF` | 1-unit outlines and dividers |
Rules: text on a filled colour uses `BRAND_FILL` with `TEXT`; never put white text on `GOOD`, `WARN` or `BAD` fills. Do not write a hex colour in a screen: use a token.

### 2.2 Shape
| Token | Value | Use |
|---|---|---|
| `R_CARD` | 14 | Posters, wide cards, app tiles |
| `R_PANEL` | 16 | Settings rows, addon rows, panels, side panels |
| `R_DIALOG` | 20 | Dialogs |
| `R_TILE` | 10 | The small rounded square behind a menu row's icon |
| pill | full | Buttons, tabs, chips, toggles, banners |

### 2.3 Spacing and type
Spacing steps: 4, 8, 12, 16, 24, 32. Row padding 16 × 12. Gap between rows 8. Screen side margin 48.
Text sizes (the only ones allowed): 11 caption, 12 and 13 small, 14 body, 15 control, 16 row title, 20 and 22 section, 24 and 26 heading, 30 hero, 34 display. Weights: regular for body, bold for controls, titles and labels.

## 3. Components
- **Focus.** A 2-unit highlight-colour ring at the component's own radius (`Ui.focusBg`, `Ui.pillBg`, `Ui.menuRowBg`, `Ui.rowStyle`), on a soft fill for rows and pills; text stays white. Never a solid block. Never remove it. A card also lifts (see section 1).
- **Menu row.** An icon on a small rounded square (`Ui.iconTile`), a bold label, an optional muted line under it, and the value, toggle or `›` at the right. Small blue headings (`Ui.sectionLabel`) group rows.
- **Selected (the open tab, the open Settings group, the chosen chip).** A 2-unit brand-blue outline on a faint fill, never a solid blue block. Cards also scale 1.06 unless lite mode is on.
- **Card (`Card`).** Rounded `R_CARD`, `CARD` fill, artwork fills it, dark fade behind any text on artwork.
- **Pill button (`Ui.button`).** `1AFFFFFF` fill, `TEXT` label; focused = highlight ring on a soft fill. Primary action may use `BRAND_FILL`.
- **Toggle (`Toggle`).** 40 × 22 pill, white knob. Off: `#39393D` track. On: `BRAND` track. Only for on/off. Changes apply immediately.
- **Choice row.** Value shown in a pill on the right with ‹ › hints. OK cycles forward, Left/Right step either way. More than 6 values opens a list dialog instead.
- **Action row.** Label and a `›` on the right. Never shows a value. Destructive actions ask first with Keep / Remove.
- **Info row.** Not focusable, dimmer outline, value on the right.
- **Panel/row container.** `CARD`, `R_PANEL`, 16 × 12 padding.
- **Dialog.** A `Sheet` (section 9): short title, one sentence of body, at most three buttons as cards, the safe choice first.
- **Banner/toast.** Pill, `ELEVATED` fill, white text. One line.
- **Progress.** 3-unit bar, `BRAND` on a 20% white track.

## 4. Menu rules
1. **Every on/off setting is a toggle.** Not a text value, not a checkbox, not an "On/Off" word.
2. A choice among 2–6 values is a **choice row**; more than 6 is a list dialog; a free value (a link, a name) is an action that opens an input dialog.
3. One control per row, on the right. Label bold on the left, one line of muted help under it (omit if the label says it all).
4. Changes apply at once. There is no Save button.
5. **Back** goes up one level and never loses a change. **Hold OK** on a row or card opens its context menu as a `Sheet.menu`. The launcher home never closes on Back.
6. **Settings is a drawer**: one rounded panel down the right edge over the dimmed screen, with no logo or date. It opens on the groups under small headings (You, Home, Watching, This TV); each group is a menu row with an icon, its name, a short summary and a `›`. OK opens the group's page, which slides in (no slide in lite mode) with a back arrow and the group's name on top; Back or Left goes up one page, then leaves. Inside a page the headings group the rows; Addons and each addon are pages one level deeper. The gear in the top bar opens it directly. Most used first. Advanced-only rows appear only in Advanced mode, directly under their group. A new group must also be added to `sections` in `SettingsActivity`, or it is not shown (`SettingsDrawerTest` checks).
   The top bar has no logo or app name: the tabs start at the left edge.
7. Destructive rows (remove, clear, reset) sit last in their group, and always confirm.
8. Order of focus is top to bottom, left to right. A screen always puts focus on its first useful control when opened.
9. Every screen has an empty, loading and error state with one sentence telling the viewer what to do.

## 5. Motion
Focus scale 110 ms. Screen change has no animation. Lite mode removes all motion. Never animate more than the focused item.

## 6. Copy
Plain words, sentence case, no jargon (say "source", not "stream resolver"; "addon", not "manifest"). A movie is a movie, not a "show". Errors say what happened and what to do next. No exclamation marks. No emoji.

## 7. Do / Don't
1. Do use `Ui` tokens and helpers. Don't write a colour, size or radius in a screen.
2. Do use a toggle for on/off. Don't use a value word.
3. Do keep one accent. Don't add a second brand colour or per-screen colours.
4. Do keep rounded shapes. Don't draw a sharp-cornered focusable.
5. Do keep the focus ring. Don't hide it.

## 8. Enforced by a test
`DesignGuardTest` fails the build if a screen (anything outside `ui/Ui.kt`) uses a hex colour, `android.graphics.Color`, a raw corner radius, a raw text size call, a text size outside the scale above, or a corner radius that is not an `R_*` token. Add the token to `Ui.kt` and to this file instead.

## 9. New screen checklist (in order, before writing code)
1. List its rows and which kind each is (toggle, choice, action, info).
2. Pick tokens for every colour, size and radius from §2.
3. Write its empty, loading and error sentences.
4. Check focus order with the D-pad only.
5. Check it in lite mode.

## 9. Dialogs and top bar
- **One pop-up: `ui/Sheet.kt`.** Every menu, question, message, confirmation, text entry and "please wait" is a `Sheet`: the same right-edge panel as Settings and the bell (`CARD`, `R_DIALOG`, `SidePanel.WIDTH`, dimmed screen behind) with a 24 title, a 14 line under it, and a list of option cards (`card`) styled like Settings rows (flat, 16 bold, blue ring when focused). Use the helper that fits: `Sheet.menu` (a list of choices), `Sheet.message` (words, an optional QR code, buttons as cards), `Sheet.ask` (one line of text), `Sheet.confirm` (the safe choice first), `Sheet.busy`. Build one by hand with `card`, `label`, `box`, `text`, `image` and `field` only when none fits.
- The first card has the focus; a primary action is outlined in the brand colour (`highlighted`), never filled; a chosen value gets a tick (`selected`). Cards have a title and at most one line under it.
- The right-hand panel (notifications) is `ui/SidePanel.kt`. Settings and Arrange (`screens/ArrangeActivity.kt`) are the same panel as whole activities in the clear `Theme.MyListsTV.Drawer`; build one with `SidePanel.drawer` and start its page with `SidePanel.backRow`. `Sheet.kt` and `SidePanel.kt` are the only places a pop-up is built. Never use `AlertDialog`, `Dialog`, `PopupMenu` or `Toast`; `DesignGuardTest.popUpsAreSheetsOrSidePanels` fails if one appears.
- Short messages at the bottom of the screen are `Pop.toast` (ui/Pop.kt), drawn by the app with no icon (the system toast shows an app icon the TV keeps from an older version).
- The same title offers the same menu everywhere (`ui/TitleMenu.kt`: Go to details, Add to or Remove from Library, Mark as watched or unwatched). Add to that one place, not to a screen.
- The top bar has the tabs on the left and, on the right, the profile chip (who is watching), then round icon buttons (`Ui.iconButton`): notifications and settings, then the clock. Search is a page in the tab row or side bar, not a button. New bar actions are icon buttons, not text pills.
- Moving along the tabs switches page; moving up from a page onto the tabs does not (OK does).
