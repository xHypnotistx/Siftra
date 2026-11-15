# Changelog

## 0.5.10 — Gemini sorting fix

- **Fixed: "timeout" when sorting with a free Gemini key.**
  - Requests now ask the model not to "think" first. Classifying app names doesn't need it, and on the free tier it made answers too slow. Models that can't turn thinking off fall back automatically.
  - Requests wait up to 90 seconds instead of 60.
  - A batch that still times out is retried as two smaller halves.
  - "Too many requests" and "busy" answers are retried once after a short wait.
- **Clear error messages** (no answer in time, no internet, free limit reached, key rejected) and a **Retry** button. Retry continues with the apps not yet placed; the ones already placed keep their group.
- **Fixed: the sorting screen was see-through** when opened from Settings. It now has its own backdrop.

## 0.5.9 — Polish & fixes

- **Fixed: the widget swipe could leave home frozen.** Home used to sit inside a lazily composed pager. In long test runs it sometimes stopped reacting once apps finished loading: the model was ready, but the screen kept showing the empty first frame. Home and the widget page are now two plain pages in a custom swipe container. Both stay composed and nothing is lazy. Six full test runs with no stall since, against stalls in five of the nine runs before.
- **Screen reader.**
  - The ⟲ and ⋯ buttons are announced as "Re-sort apps" and "Settings".
  - The search bar and "All apps" have proper names, and the clock offers "Change clock style".
  - App names are no longer read twice (icon and label).
  - Status messages are announced when they appear.
  - Only the visible page (home or widgets) is exposed.
- **Honest Re-sort.** Without a Gemini key, ⟲ says how many apps are still in Other and where to add a key. Before, it said "Sorted ✓" even when nothing happened. Every status message now clears itself.
- **First start** says "Sorting your apps…" instead of showing an empty home while the list loads.
- **Phones keep home upright.** The layouts are built for portrait. Tablets and unfolded foldables can still rotate.
- If an app can't be opened, Siftra says so instead of doing nothing.
- The Gemini progress screen lists at most 60 apps; the ring still counts all of them. This keeps a full re-sort of hundreds of apps smooth.
- Welcome mentions the widget page and Settings › Gestures & dots.
- README rewritten for everything in 0.5.x.
- Tests: 31 passing, including labels, the no-key message clearing itself and the orientation lock. Each UI test now flushes pending frames at teardown.

## 0.5.8 — Smarter sorting

- **Learns from your moves.** If every app you moved from one developer went to the same group, that developer's other apps go there too.
  - Example: move Duolingo to your "Study" group and Duolingo ABC follows.
  - It only applies to apps the built-in rules don't recognise. It beats an older Gemini guess, and it means Gemini isn't asked about those apps at all.
  - Moves from one developer into different groups teach nothing.
- **Wider offline rules.** About 150 new keywords, many of them Turkish:
  - Banks, wallets and meal cards (Paycell, Sipay, Multinet, Pluxee…), plus brokers and crypto exchanges.
  - Shops and food chains (ŞOK, BİM, PttAVM, LC Waikiki, Teknosa…).
  - Transport (Martı, İETT, İZBAN, TCDD, AJet…), plus Turkish TV and news (TRT, tabii, puhutv, beIN…).
  - Game studios (Peak, Dream Games, Rollic, HoYoverse…), AI assistants and tools.
  - Government (ÖSYM, MEB, PTT, İBB, İŞKUR, belediye apps…).
- **Turkish letters fold before matching**, so "İŞCEP", "İşCep" and "iscep" are the same word.
- **Fewer look-alike mistakes.** Keywords that would have misfiled apps were left out on purpose: Kick the Buddy stays in Games, the Flo period tracker isn't sent to Shopping, Paramount+ isn't sent to Finance.
- **Fewer Gemini calls.** Up to 12 apps per request instead of 5.
- **The Move sheet says why an app is where it is**: moved by you, sorted on your phone, learned from your moves, or placed by Gemini.
- Tests: 30 passing. The rule catalog grew to 95 real apps, all sorted correctly. New tests cover learning, developer prefixes, Turkish folding and a late app load. The test harness also nudges Robolectric's fake clock if a long run stalls.

## 0.5.7 — Widgets page

- **A widget page, one swipe right from home.** Any Android widget can live there: weather, music, calendar, notes. The group layouts are untouched.
  - The Home button and Back both return to home.
  - Swiping is paused while a group, the drawer or a menu is open.
- **Widget picker.** Every widget on the phone, grouped by app, with previews, cell sizes and a search field. Work-profile widgets are included.
  - Adding a widget asks Android for permission only the first time.
  - Widgets that need setup open their own setup screen. Cancelling it removes the widget again.
- **Sizes and layout.** Widgets start at the size they ask for. Wide ones take the full width, small ones sit two to a row.
- **Edit mode:** move up or down, make shorter or taller in 40 dp steps, switch between half and full width, or remove.
- **Housekeeping.** Widgets only update while Siftra is on screen. A widget whose app was removed shows a short note with a Remove button. Widget slots Android still holds from interrupted adds are freed on start.
- Tests: 28 passing, including the full page flow (swipe to the page, pick, add, edit, remove, restart, Home press).

## 0.5.6 — Notification dots, swipe down, double-tap to lock

- **Notification dots.** A dot on every app with unread notifications, and a dot next to the count of any group holding one.
  - It follows the same rules as the stock launcher: it respects each app's "show dot" setting, and skips group headers, empty notifications and ongoing legacy ones.
  - Siftra only counts notifications; it never reads or stores what they say.
  - Needs notification access once.
- **Swipe down for notifications** on the clock, or on the groups when they're scrolled to the top. On by default.
- **Double-tap empty space to lock.** Needs Siftra's lock service, a minimal accessibility service that asks for no events and can't read the screen. Fingerprint and face unlock keep working. When the service is on, it also makes swipe-down work on every phone.
- New **Settings › Gestures & dots** page. It walks through both permissions, including the "Allow restricted settings" step that APK installs need on Android 13 and later, and re-checks them when you come back from system settings.
- Tests: 27 passing, including dots on apps and groups, turning dots off, the swipe-down gesture and the new settings page.

## 0.5.5 — Speed & battery

- **Glass is much cheaper to draw.** The backdrop is blurred once, at quarter size, and every glass panel samples that copy. Before, each card, row and sheet blurred its own piece of the backdrop on every frame. The frosted/ribbed texture still runs per panel at full sharpness, with a lighter noise function.
- **No more endless 120 Hz redraws.**
  - The animated backdrop now steps 30 times a second (its blobs drift over ~48 s, so it looks the same). Between steps the phone can drop to a low refresh rate.
  - The Minimal clock's blinking colon (the Index Lime default) repainted on every frame, even with motion off. It now repaints once a second.
  - Index Lime's living group colours run at up to 60 fps while a group is open, and at full speed only while you drag.
- **Fast cold start.** App names and icons are kept on the phone and reused until an app is updated, so starting Siftra no longer opens every installed APK. Installing, updating or removing one app now reads only that app instead of all of them. Apps that do need reading are read in parallel, with one icon load per app instead of two.
- Icons from the cache go straight into GPU memory, so scrolling the drawer doesn't upload them and they take less RAM.
- Siftra's own code is now precompiled on install (baseline profile).
- Settings › Apps › **App icons & names › Refresh** rebuilds the cache, for example after changing the icon style in phone settings. About shows how the last start was served.
- Tests: 25 passing, including an icon cache round trip (reuse, update, uninstall cleanup, invalidation, corrupt index).

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
