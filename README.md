# LUMEN Android

Android streaming catalog app using Cinemeta movie and series metadata. Playback supports authorized, user-configured third-party streams.

Build: GitHub Actions > Build Android APK, or run `gradle assembleDebug` using JDK 17 and Android SDK 35. The APK is in `app/build/outputs/apk/debug/app-debug.apk`.

Cinemeta is a metadata provider, not a license to stream copyrighted movies or television. Only use playback sources that you are authorized to access.


v1.2: Clicking Watch shows an external-browser Vidlink option first; in-app embeds are experimental. The app now recovers if the Android WebView renderer exits, instead of terminating. If playback fails, use the system browser. No third-party title availability is guaranteed.
