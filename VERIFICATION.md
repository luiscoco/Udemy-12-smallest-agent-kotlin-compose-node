# Verification · 2026-11-09

Completed on Windows with Node.js, JDK 21.0.8 and the installed Android SDK platform 36 / Build Tools 36.0.0.

| Check | Actual result |
| --- | --- |
| Backend TypeScript typecheck and production build | Passed |
| Backend Vitest suite | 12 tests passed; SDK fixtures, no live inference |
| Generated lesson content | 16 source-linked cards and matching SVG/PlantUML labels generated |
| Native Android client tests | 7 JUnit tests passed: MockWebServer requests/results, SSE boundaries, errors, invalid input and cancellation |
| Native Compose UI tests | 3 Robolectric tests passed: five tabs, retained sample state, architecture content and syntax spans |
| Android lintDebug | Passed with no errors; warnings remain |
| Android assembleDebug | Passed; actual installable debug APK produced |
| Real Node HTTP checks | 15 passed with the API key explicitly overridden to the placeholder |

The HTTP checks covered health, custom API docs/OpenAPI, malformed JSON, exact media type, forbidden Origin, oversized body, invalid prompt, missing-key JSON/SSE responses and unknown routes. Health was asserted to report api_key_configured=false before any POST. No paid/provider inference was performed, and no previous application's key was read or reused.

The APK is `android/app/build/outputs/apk/debug/app-debug.apk`. Unit-test XML reports are under `android/app/build/test-results/testDebugUnitTest`; lint HTML/SARIF reports are under `android/app/build/reports`. Build outputs and tool caches are ignored by source control.

The initial Windows resource build exposed an AAPT2 executable path-length failure. Running the identical compiler from the shorter project .tools/aapt2 location resolved it; scripts/Android.ps1 implements that override with a pinned official archive checksum. The installed SDK emitted read-only package metadata warnings, and AGP labels the compiler override experimental; both were nonfatal. Compose reports a deprecated ScrollableTabRow API. Lint also recommends newer dependency/target versions and flags backup metadata, icon packaging, drawing allocation and Kotlin convenience APIs. These warnings have not been suppressed or represented as errors.

No Android emulator is installed here, and the APK was not installed on a physical phone. Robolectric checks are native Compose/JVM tests, not device verification. Actual device networking, document picker/share intents, rotation on hardware, accessibility/font scaling, visual appearance and live Claude authentication/model access still require an emulator or device and the user's server-side key. Release signing, release APK testing, production hosting and store submission were not performed.

The backend and lesson code use only this edition's own dependencies and files. The Android application contains no API key, server environment file, Node runtime or browser frontend. See README.md for emulator, USB and LAN connection settings and the release HTTPS requirement.
