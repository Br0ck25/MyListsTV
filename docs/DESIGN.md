# DESIGN.md — My Lists TV look and menu rules

> Audience: anyone (human or AI) adding or changing a screen. Follow every rule. Tokens live in `ui/Ui.kt`; if a token is missing, add it there **and** here.
> Decision (owner asked, assistant decided): **use the My Lists palette** from the website's design system (`My-Lists-Addon/docs/DESIGN_SYSTEM.md`, dark values), with larger rounded shapes in the spirit of Projectivy and Material "Monet" panels. The site and the TV then look like one product, and every colour pair below was already checked for contrast (5:1 or better against white text).

## 1. Look in one paragraph
True-black canvas, soft dark-grey rounded panels, white text, one blue brand colour. Everything focusable is rounded; buttons, tabs and toggles are full pills. Focus is always a white ring (and a small scale-up when not in lite mode). Blue means "on, active or primary". No gradients except the dark fade behind text on artwork. No second accent colour.

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
| `BRAND` | `#0A84FF` | Active state, primary button fill, progress, toggle on, meta accents |
| `BRAND_FILL` | `#0A64D8` | Fills that carry white text (≥ 5:1) |
| `GOOD` | `#30D158` | Success text, "Cached" |
| `WARN` | `#FF9F0A` | Warnings |
| `BAD` | `#FF453A` | Errors, destructive actions |
| `LINE` | `#24FFFFFF` | 1-unit outlines and dividers |
Rules: text on a filled colour uses `BRAND_FILL` with `TEXT`; never put white text on `GOOD`, `WARN` or `BAD` fills. Do not write a hex colour in a screen: use a token.

### 2.2 Shape
| Token | Value | Use |
|---|---|---|
| `R_CARD` | 14 | Posters, wide cards, app tiles |
| `R_PANEL` | 16 | Settings rows, addon rows, panels, side panels |
| `R_DIALOG` | 20 | Dialogs |
| pill | full | Buttons, tabs, chips, toggles, banners |

### 2.3 Spacing and type
Spacing steps: 4, 8, 12, 16, 24, 32. Row padding 16 × 12. Gap between rows 8. Screen side margin 48.
Text sizes: caption 11, small 12, body 14, control 15, row title 16, heading 24, hero 26. Weights: regular for body, bold for controls, titles and labels. No other sizes.

## 3. Components
- **Focus.** White 3-unit ring at the component's own radius, soft white fill (≈13%). Never remove it. Cards also scale 1.06 unless lite mode is on.
- **Card (`Card`).** Rounded `R_CARD`, `CARD` fill, artwork fills it, dark fade behind any text on artwork.
- **Pill button (`Ui.button`).** `1AFFFFFF` fill, `TEXT` label; focused = white fill, black label. Primary action may use `BRAND_FILL`.
- **Toggle (`Toggle`).** 40 × 22 pill, white knob. Off: `#39393D` track. On: `BRAND` track. Only for on/off. Changes apply immediately.
- **Choice row.** Value shown in a pill on the right with ‹ › hints. OK cycles forward, Left/Right step either way. More than 6 values opens a list dialog instead.
- **Action row.** Label and a `›` on the right. Never shows a value. Destructive actions ask first with Keep / Remove.
- **Info row.** Not focusable, dimmer outline, value on the right.
- **Panel/row container.** `CARD`, `R_PANEL`, 16 × 12 padding.
- **Dialog.** System dialog in the dark theme, short title, one sentence of body, at most three buttons, the safe choice first.
- **Banner/toast.** Pill, `ELEVATED` fill, white text. One line.
- **Progress.** 3-unit bar, `BRAND` on a 20% white track.

## 4. Menu rules
1. **Every on/off setting is a toggle.** Not a text value, not a checkbox, not an "On/Off" word.
2. A choice among 2–6 values is a **choice row**; more than 6 is a list dialog; a free value (a link, a name) is an action that opens an input dialog.
3. One control per row, on the right. Label bold on the left, one line of muted help under it (omit if the label says it all).
4. Changes apply at once. There is no Save button.
5. **Back** goes up one level and never loses a change. **Hold OK** on a row or card opens its context menu as a list dialog. The launcher home never closes on Back.
6. Settings are grouped under short headings (Playback, Home, Live TV, Backup, Profiles, Updates, About). Most used first. Advanced-only rows appear only in Advanced mode, directly under their group.
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

## 8. New screen checklist (in order, before writing code)
1. List its rows and which kind each is (toggle, choice, action, info).
2. Pick tokens for every colour, size and radius from §2.
3. Write its empty, loading and error sentences.
4. Check focus order with the D-pad only.
5. Check it in lite mode.
