# Siftra

**Sift + Strata.** A free, open-source Android launcher that sorts your apps into clean layers on its own, wrapped in frosted glass and fluid motion.

## Features

- **Auto-grouping.** Every app lands in a group: Government · Inspiration (Pinterest, Savee, Are.na, Cosmos, Behance…) · Samsung Essentials · Samsung Extras (bloat, folded away) · Google · Social · Media · Games · Finance · Shopping · Travel · Productivity · Tools · Made by Mahmut. You can also make your own groups.
- **Smart, private sorting.**
  - On-device rules sort almost everything offline. They use package names, about 400 keywords (with Turkish letters folded), Android's app category and system flags.
  - Siftra learns from your moves: move one app from a developer and their other apps follow.
  - Optionally, your own Gemini API key labels the few unknown apps. Only app names and package names are sent, once per new app, 12 apps per request.
  - The Move sheet tells you why each app is where it is.
- **Home screen.** Frosted group cards that expand into a full panel with a container-transform animation. A shelf you swipe between **Most used** and **Favorites**.
- **Widgets page.** Swipe right on home. Add any Android widget from a picker with previews, then resize, reorder or remove it.
- **Notification dots** on apps and on their groups. Siftra only counts notifications; it never reads them.
- **Gestures.** Swipe down for notifications. Double-tap empty space to lock, using a minimal accessibility service that can't read the screen.
- **App drawer.** Swipe up. A Groups or A–Z view with a letter rail, search with a top hit, recent searches, and web or Play Store fallbacks.
- **Long-press menu.** Favorite, move (including to a new group), hide, app info, uninstall, and the app's own shortcuts.
- **Five home layouts.**
  - Index Lime: numbered editorial list, lime on dark, living group colours.
  - Cards.
  - Strata: offset glass layers.
  - Monolith: ribbed bento with a giant clock.
  - Orbit: glass bubbles sized by app count.
  - Every screen (settings, drawer, sheets) follows the chosen layout's look.
- **Material You.** Accent colors, a "You" backdrop and themed monochrome icons, all from your system palette.
- **Classy type.** Instrument Serif and Instrument Sans (SIL OFL, see `OFL-fonts.txt`), plus five clock styles. Tap the clock to switch.
- **Textured glass.** A frosted or ribbed AGSL distortion plus film grain, over Siftra's animated backdrops or your own photo.
  - The backdrop is blurred once at quarter size and shared by every panel.
  - Animation is capped at 30 fps and stops when nothing moves.
- **Fast start.** App names and icons are cached on the phone, decoded straight into GPU memory, and re-read only for apps that changed.
- **No tracking.** Launch history ("Most used") stays on your phone.

See [CHANGELOG.md](CHANGELOG.md) for every version.

## Build

Requirements: JDK 17+ and Android SDK 36.

```
./gradlew assembleRelease
```

- Kotlin + Jetpack Compose (foundation only, no Material).
- minSdk 31.

## Contributing

PRs welcome, especially:
- more apps in the rule lists (`Rules` in `Model.kt`), e.g. regional government and bank apps
- translations
- icon packs

## License

MIT
