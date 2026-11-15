# Siftra

**Sift + Strata.** A free, open-source Android launcher that sorts your apps into clean layers on its own, wrapped in frosted glass and fluid motion.

## Features

- **Auto-grouping.** Every app lands in a group: Government · Inspiration (Pinterest, Savee, Are.na, Cosmos, Behance…) · Samsung Essentials · Samsung Extras (bloat, folded away) · Google · Social · Media · Games · Finance · Shopping · Travel · Productivity · Tools · Made by Mahmut.
- **Smart, private sorting.** On-device rules (package names, keywords, Android's app category, system flags) sort almost everything offline. Optionally, your own Gemini API key labels the few unknown apps. Only app names and package names are sent, once per new app.
- **Home screen.** Frosted group cards that expand into a full panel with a container-transform animation. A shelf you swipe left and right between **Most used** and **Favorites**.
- **App drawer.** Swipe up. Grouped sections, jump chips, instant search.
- **Organize anywhere.** The ⟲ button re-sorts on the home screen and in the drawer. Long-press any app to favorite, move, hide, see app info or uninstall.
- **Material You.** Accent colors, a "You" backdrop and themed monochrome icons, all from your system palette.
- **Classy type.** Instrument Serif and Instrument Sans (SIL OFL, see `OFL-fonts.txt`), plus five clock styles: Classic, Stacked, Split, Analog and Minimal. Tap the clock to switch.
- **Textured glass.** Blur plus an AGSL frosted or ribbed distortion plus film grain, over Siftra's animated backdrops or your own photo.
- **Real blur.** The wallpaper blurs behind open panels (Android 12+ cross-window blur).
- **No tracking.** "Most used" is counted on-device only.

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
