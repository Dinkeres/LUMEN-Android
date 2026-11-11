# LUMEN Android

Android streaming catalog app using Cinemeta movie and series metadata. Playback supports authorized, user-configured third-party streams.

Build: GitHub Actions > Build Android APK, or run `gradle assembleDebug` using JDK 17 and Android SDK 35. The APK is in `app/build/outputs/apk/debug/app-debug.apk`.

Cinemeta is a metadata provider, not a license to stream copyrighted movies or television. Only use playback sources that you are authorized to access.


v1.2: Clicking Watch shows an external-browser Vidlink option first; in-app embeds are experimental. The app now recovers if the Android WebView renderer exits, instead of terminating. If playback fails, use the system browser. No third-party title availability is guaranteed.

## LUMEN v1.3 — In-app mobile player
Vidlink opens in a dedicated full-screen Android Activity inside LUMEN, not a browser or embedded iframe in the catalog. The player runs in a separate app process to protect the movie catalog from unstable web video engines. Vidlink has two modes: Mobile / JW and Standard. Actual provider playback depends on its remote stream and Android WebView compatibility.

A 'Test video' button in the catalog plays MDN's CC0 Flower sample in Android Media3 ExoPlayer. Authorized direct MP4 and HLS sources also use Media3. If this clip plays but Vidlink fails, the likely issue is the third-party embed, not the native Android player.
