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
