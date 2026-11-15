# Changelog

## 0.5.4 — Concept redesign, stage 3: groups, shelf pages, Gemini progress

- **New long-press app menu.** A themed bottom sheet replaces the old pop-up. It shows the app's group and how many times you opened it this week, five actions (Favorite, Move, Hide, Info, Uninstall), and the app's own shortcuts with their icons. Shortcuts only appear while Siftra is your default home.
- **Your own groups.** Move › "New group…" creates a group and moves the app into it. Gemini sees your group names when it re-sorts and can place apps there.
- **Rename or delete groups.** Long-press a group's title on any home layout or in the drawer, or tap one of your groups in Settings › Sorting. Deleting a group sends its apps back to their automatic groups. Built-in groups can't be renamed and offer a shortcut to reordering instead.
- **Favorites editor** (Settings › Apps › Favorites): remove with ×, long-press and drag to reorder, and add apps from a searchable list.
- **Most used** (Settings › Apps › Most used): Today, This week and All time, with a top-3 card and bars for the rest.
- **Gemini sorting screen.** Pressing Re-sort (home, drawer or Settings) shows live progress: a ring, each app as it's placed, and any error. "Run in background" hides it. Automatic sorting stays silent with a short message.
- Tests: 24 passing, including custom groups (create, rename, delete, restart, Gemini answers with a custom group) and a full UI run through the menu, Move, group editing, Favorites, Most used and the progress screen.

## 0.5.3 (2026-11-15): stage 2 of the concept redesign (app drawer + search)

- **Themed drawer.** Follows the selected layout's style and colors (including Orbit's light look). Grab handle; swipe down on the header to close.
- **Groups / A–Z switch,** remembered between visits.
  - *Groups*: section headers in the layout's style with counts, each group in one glass card, and chips that scroll to a group.
  - *A–Z*: alphabetical grid with letter headers (Turkish letters included) and a side rail. Tap or drag it to jump, with a large letter bubble while dragging.
- **Search.** Smarter ranking: name start, then word start, then anywhere in the name, then letters in order (handles Turkish characters, e.g. "iscep" finds İşCep), with often-opened apps first.
  - *Top hit card*: big icon, group, how many times it was opened today, Open and App info buttons. Long-press opens the app menu.
  - **Enter / Go opens the top hit.**
  - *Settings matches*: e.g. "gemini" offers *Settings › Sorting & Gemini* and opens Settings right on that page.
  - *Search the web* and *Search Play Store* rows.
  - *No results*: "Nothing called “…”" with a *Show hidden apps* option.
  - *Recent searches*: last 6, kept on the phone. Shown as chips in the drawer and under results; tap to search again.
- **Tests.** New `drawerAzEnterRecentSettings` test (A–Z switch and rail, Enter opens top hit, recents, settings from search). All 22 pass.

## 0.5.2 (2026-11-15): stage 1 of the concept redesign (Settings + Welcome)

- **Per-layout styling for every new screen.** A shared kit (rows, labels, chips, switches, buttons, icon tiles, line glyphs, layout thumbnails) follows the selected home layout: Index Lime numbered lists, Cards frosted cards, Strata offset layers, Monolith caps and ribbed glass, Orbit light pills.
- **Settings rebuilt as sections** with smooth page transitions (Back goes up a level):
  - *Home*: default-home banner plus Look, Home layout, Sorting & Gemini, Apps, About.
  - *Look*: live mini preview; backdrop, glass, clock and font chips; Material You, themed icons and animated backdrop switches.
  - *Home layout*: tap a thumbnail of each layout.
  - *Sorting & Gemini*: counts sorted by rules, by Gemini and by you; Gemini key field that checks the key (Valid / Invalid); drag-to-reorder group order (applies to every layout); Samsung Extras switch; re-sort.
  - *Apps*: hidden apps with when they were hidden and per-app Unhide; manual moves with Reset.
  - *About*: version, GitHub, license, fonts.
- **Welcome (first run).** Three steps: intro with your real top groups, pick a layout, make Siftra your home screen (or "Try it first").
- **New data, on the phone only.** Dated launch history (60 days, for Today / This week in stage 3), the date each app was hidden, and a saved group order.
- **Tests.** Settings test walks every page and control and checks drag-to-reorder. New Welcome flow test and launch-history / group-order test. All 21 pass.

## 0.5.1 (2026-11-15): calmer Index Lime

- **Removed all bouncing and floating text motion.** The Media equalizer, Shopping drop, Social heartbeat and the floating title on an unfolded group are gone. The title still grows to italic when unfolded, now without overshoot.
- **Living colors only on interaction.** Google's rotating colors, the glints, the iridescent sheen, the Games glitch, the pixel flicker and the Productivity cursor run only while a group is unfolded, armed or being dragged. Otherwise each group shows calm, still colors (for example Google's four colors fixed per letter, and Turkish red).
- **Softer entrance.** Letters rise less, with no tilt and a lighter blur.
- **Battery.** The animation clock now runs only while a group is being interacted with, so the home screen is fully still when idle.

## 0.5.0 (2026-11-15): Index Lime

Index is now **Index Lime**, which replaces the grey Index.

- **Look.** Lime (#C6FF3D) on a near-black olive backdrop, ribbed glass, film grain. New *Lime* backdrop. The clock colon blinks in lime. App icons are lime-themed by default; switch to real icons with *Themed icons* in settings.
- **Living titles.** Each group name rises in letter by letter (fade, lift, blur clearing), then keeps moving in its own theme:
  Made by Mahmut (lime, pixel flicker) · Government (Turkish red with a white glint, crescent and star) · Inspiration (iridescent) · Samsung (blue highlight) · Google (blue, red, yellow and green slowly rotating) · Social (pink to orange, heartbeat) · Media (equalizer bounce) · Games (neon cyan, glitch) · Finance (emerald, gold glint) · Shopping (tangerine bounce) · Travel (sky-to-sunset drift) · Productivity (typewriter cursor) · Tools (scan line).
- **Unfold.** Tap a group to unfold its apps in place. The drawer springs open with a slight overshoot, and icons pop in one after another. One group is open at a time; Back folds it.
- **Double-tap, then drag down.** Double-tap a group to arm it (subtle haptic tick and a pulsing *DRAG ↓* tag). Then drag down: the drawer follows your finger and rubber-bands past full. Release past about a third and it springs open with a confirm haptic; otherwise it snaps back. Works as one continuous gesture or as a double-tap followed by a separate drag. The hint line hides after the first successful drag.
- **Big groups.** Groups with more than 8 apps unfold into a horizontal scrolling row; smaller ones use a grid.
- **Battery.** All motion (titles, backdrop, clock colon) runs only while the home screen is visible and pauses when an app is open or the screen is off.
- **Tests.** New `indexDoubleTapDrag` test (full drag opens, short drag snaps back, Back folds); full suite passes.

## 0.4.1 (2026-11-15): stress test fixes

Found by the new Robolectric stress suite (18 tests, all passing).

### Fixed

- **Turkish banks sorted into Productivity** (`Model.kt`, Finance keywords). İşCep, Kuveyt Türk, Türkiye Finans and ON Dijital declare Android's *Productivity* category, and that category won over the rules. Added Turkish bank and fintech names: iscep, kuveyt, finans, ondijital, money, fibabanka, odeabank, şekerbank, albaraka, hsbc, teb, ing, vakıf, bankası, bankacılık, midas, colendi.
- **Utilities landed in Other** (`Model.kt`, new Tools keyword list). Microsoft SwiftKey, MS Defender, Tor VPN, RAR and Türk Telekom matched no rule. The new list covers VPN, antivirus, archivers, carriers, keyboards, scanners, translators, authenticators and similar.
- **Taps fell through the app drawer** (`MainActivity.kt`). With the drawer open, the faded-out home screen was still live, so a tap on empty drawer space could open a hidden group or app. The drawer now swallows stray taps, and the hidden home screen is inert (no touches, no accessibility focus) while the drawer or settings are open.
- **Monolith clock spacing** (`Theme.kt`). Hour and minute digits were rendered far apart. They are now tightly stacked as in the design, and the date sits beside them.
- **Safer system calls** (`MainActivity.kt`, `Model.kt`, `Glass.kt`). "Set as default home" and "App info" can no longer crash the launcher on devices that refuse those requests. If the textured-glass GPU effect fails to load, Siftra falls back to plain blur instead of crashing.
- **Gemini answer parsing** (`Model.kt`). Answers wrapped in code fences or extra text, unknown group names and missing apps are handled cleanly. An answer without JSON shows an error message instead of a crash.

### Added

- Test suite in `app/src/test`. Run it with `./gradlew testDebugUnitTest`.
  - `LogicStressTest`: sorting for a 78-app Turkish S23 catalog, 2,000+ apps, weird input, 5,000 random state operations, persistence across restart, layout presets, Gemini with no key or a bad key, answer parsing.
  - `UiStressTest`: every layout opening and closing a group; drawer search, chips, favorite, hide and move; a drawer tap-through regression check; every settings control; 60 rapid layout switches; animated backdrop frames; a random-input "monkey" run across all layouts.
- `TestHooks` (`Model.kt`) lets tests supply a fake app list.

### Not covered

The tests can't measure GPU rendering (blur, the AGSL glass shader), real-device frame rate or battery use, because no emulator or device was available.

## 0.4.0

Home layouts Strata, Monolith, Orbit and Index; Pastel backdrop; Monolith clock; dark-text mode for light backdrops.

## 0.3.0

Material You colors and themed icons, Instrument Serif and Instrument Sans fonts, five clock styles.

## 0.2.0

Textured glass (blur, frosted or ribbed distortion, grain), Siftra backdrops, see-through group panel fixed.

## 0.1.0

First release: automatic grouping, frosted home screen, app drawer, shelf, optional Gemini sorting.
