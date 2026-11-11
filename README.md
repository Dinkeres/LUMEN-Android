# KaiserWatch — Android

A cinematic Android movie and television catalog using Cinemeta metadata, with a single optional third-party playback provider: Vidlink.

## Highlights
- Dedicated in-app Vidlink player. No external browser or additional provider menus.
- Premium red/gold interface with featured title, search, movie and series browsing.
- Continue Watching (last opened title / last opened episode), watch history, saved titles, and watched labels.
- Episode picker and manual watched/unwatched controls.
- Local backup/export/import tools in Settings.

## Playback and limitations
Vidlink provides a web-embedded player. It cannot be rendered by Android Media3/ExoPlayer as native video without a compatible authorized direct media URL. KaiserWatch uses a native Android Activity with a WebView for that embed. The in-app player was previously confirmed on the user's Android tablet (v1.3); v2.0 requires re-testing.

Continue Watching records opened titles and episodes, not guaranteed exact video timestamp. The third-party player may separately retain its own progress. All library data is local to the Android app's WebView storage. It is not synced to an account. Back it up before uninstalling. Use only video sources that you are entitled to access.

## Build
GitHub Actions > Build Android APK; download the APK artifact. Debug builds are not production signed and their certificate may change between workflows, in which case uninstalling the previous APK may be required.

Brand: KaiserWatch. The Android launcher and in-app header use the supplied pirate-themed artwork, scaled for a compact icon.

## v2.1 — Netflix-style viewing
One Play button launches the existing in-app Vidlink player engine (JW) in a dedicated full-screen activity. There are no engine/server selection buttons. Movies show only Back; TV shows add always-available Episodes and Next controls. The episode drawer provides season selection, recently released badges and the full episode list. Choosing an episode reloads the same embedded provider in-place. On return to the catalog, the selected episode is saved to Continue Watching. Vidlink's own nextbutton remains enabled, but its timing and source availability are controlled by the third party.

## v2.2 — Live search + episode panel
- Search filters visible titles instantly as characters are typed and performs a debounced Cinemeta search at 2+ characters. Previous responses cannot overwrite newer searches.
- Search includes scrollable suggestions and landscape title cards.
- The video toolbar has a stacked-screens icon button for the episode picker, while retaining the Next Episode control.
- The native episode overlay has a season selector, episode images where Cinemeta provides them, descriptions, currently-playing status and new-episode labels.
- Progress is **not** fabricated: only episodes explicitly marked watched in the local library receive a completed marker. Exact playback positions are managed by the third-party player when supported.
