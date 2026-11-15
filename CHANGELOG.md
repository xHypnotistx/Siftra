# Changelog

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
