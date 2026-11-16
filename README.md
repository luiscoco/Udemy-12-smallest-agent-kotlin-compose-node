# The Smallest Agent · Kotlin + Jetpack Compose / Node.js + TypeScript

Independent edition 12: a **native Android application**, with a separate Node server running the real TypeScript Claude Agent SDK. The interface uses Kotlin, Compose and Material 3. It includes five lesson tabs, a real agent lab, large text, pastel cards, persistent theme/API settings, syntax-colored snippets, a 16-step UML workflow and source explanations generated from this edition's actual files.

This is opened on Android rather than as a browser frontend. The API and its custom reference page run on your PC.

## 1. Start the Node backend on Windows

Use Node.js 24 LTS. From **Lesson 0**, open PowerShell:

```powershell
cd .\12-smallest-agent-kotlin-compose-node
npm install
npm run setup
if (!(Test-Path backend\.env)) { Copy-Item backend\.env.example backend\.env }
notepad backend\.env
```

Set your own `ANTHROPIC_API_KEY`, save, then run:

```powershell
npm run dev
```

Health: **http://127.0.0.1:8700/api/health**. Custom API reference: **http://127.0.0.1:8700/docs**, with an OpenAPI document at **/openapi.json**. This reference is not Swagger UI. Stop Node with Ctrl+C. `Start-Backend.ps1` resolves its own directory. No Python, .NET or previous application is needed.

Private backend/.env:

```dotenv
ANTHROPIC_API_KEY=sk-ant-your-own-key
CLAUDE_MODEL=
AGENT_SYSTEM_PROMPT=You are a concise teaching assistant.
AGENT_MAX_TURNS=1
API_HOST=127.0.0.1
API_PORT=8700
ALLOWED_ORIGINS=
```

Only this file contains credentials. It is loaded relative to the backend module; process environment overrides the file. Restart after editing. Empty CLAUDE_MODEL uses SDK default; model access depends on your account. Turns default to 1 and accept integers 1–10. Placeholder keys count as missing. Health shows presence rather than authenticated readiness or available credits.

Native clients do not send a browser Origin. ALLOWED_ORIGINS is empty by default; add exact protocol/host/port values only for browser integrations. CORS is not authentication. The Android APK never contains your Claude key.

## 2. Build and open Android

Use a recent Android Studio that supports AGP 9.3, or the included Gradle wrapper with JDK 17+ and Android SDK platform 36 / Build Tools 36.0.0. This edition pins compatible versions; see the Gradle files. Open the **android** folder in Android Studio, let it sync and choose an API 26+ emulator or device. Run **app**.

You can also use a second PowerShell terminal, from the application root:

```powershell
npm run android:build
```

The Windows helper uses JAVA_HOME / ANDROID_HOME when set, or discovers the Android Studio/Visual Studio locations used on this machine. It keeps Gradle downloads in the ignored project .tools folder. The wrapper download has a pinned SHA-256 checksum. Build output is **android/app/build/outputs/apk/debug/app-debug.apk**.

This workspace's long Windows path prevents AAPT2 from starting from Gradle's cache. The helper places the exact AGP resource compiler at a shorter project path, verifies its official archive checksum on download and passes the compiler override to Gradle. If Android Studio encounters that error, use `npm run android:build` or move this independent edition to a shorter directory. Read-only SDK metadata warnings on this machine did not prevent the build.

With one authorized Android device/emulator attached:

```powershell
npm run android:install
```

Alternatively install the debug APK from Android Studio or transfer it to a test phone and permit installation when Android asks. Device installation, native IDE launch, release signing and Play Store submission are explicit user actions; this scaffold does not perform them automatically.

On macOS/Linux use the android/gradlew script with your JDK/SDK environment. The root convenience Android scripts target Windows PowerShell.

## 3. Connect Android to the backend

Open **How to run** in the Android app and edit **API base URL**, then tap **Save API URL** / **Check connection**. The public URL and light/dark preference are stored in Android SharedPreferences. Do not enter the Claude key into Android.

- Android emulator: **http://10.0.2.2:8700** (the default).
- USB-connected phone: run `adb reverse tcp:8700 tcp:8700`, then set **http://127.0.0.1:8700** in the app. Add platform-tools to PATH or invoke adb by its installed path.
- LAN phone: set API_HOST=0.0.0.0 in the private backend file, restart Node, use the PC's LAN IP in the app and permit the API port through Windows Firewall. Both devices need a reachable network.
- A phone's localhost points to the phone, not your PC.
- Release builds require an **HTTPS** API URL. Only the debug manifest enables local cleartext HTTP. HTTPS endpoints need a trusted certificate; there is no trust-all certificate bypass.

The UI works without a key, including all lessons. Query then returns a safe 503; stream emits a safe error without done, without invoking the SDK. Provider authentication and credits are needed only for real inference.

## 4. Checks and builds

```powershell
# Application root
npm run generate:lesson
npm run typecheck
npm test
npm run build
npm run android:test
npm run android:lint
npm run android:build
```

For HTTP verification, start the backend in a separate terminal with `$env:ANTHROPIC_API_KEY='sk-ant-replace-with-your-key'` before starting it, then run `node scripts/verify-http.mjs`. This check requires health to report no configured key and exercises validation and missing-key responses without inference.

Root build compiles the Node backend and regenerates lesson assets. Android build produces the native debug APK. Android tests include local SSE/HTTP fixtures and native Compose UI checks with Robolectric; they do not require a connected device or make paid model calls. Release signing is deliberately separate.

The Node backend has its own package-lock.json. For repeat installs use `npm ci` and `npm --prefix backend ci`. All runtime/build dependencies belong to this edition; Gradle resolves pinned Android dependencies through Google/Maven repositories. First builds download the toolchain and test artifacts and may take several minutes.

After a Node production build, run `npm --prefix backend start` from this project root. It still loads only its own backend/.env. Android remains a separate native application.

## Learning flow

- **Agent lab:** trimmed prompt counter, examples, Stream/Simple, immediate duplicate-submit lock, elapsed time, cancel, answer/metrics/error state, copy, native document save and share sheet. Timestamped safe SDK messages expand to colored JSON. A LazyColumn virtualizes rows; the visible timeline caps at 500 while final results still process.
- **Learn:** six substantial explanations of native UI, SDK loop, private configuration, streaming, isolation and final result semantics.
- **How to run:** exact Windows instructions, public API URL settings, emulator/USB/LAN guidance, build/check commands and private server configuration examples.
- **Architecture:** local AndroidSVG-rendered UML with five lifelines, 16 numbered arrows, a loop and success/error/input alternatives. Each arrow has a colored real-source excerpt. SVG and editable PlantUML can be saved with Android's document picker.
- **Walkthrough:** five actual Kotlin/TypeScript excerpts from this application.

The activity-scoped ViewModel retains lab state through tab navigation and Android configuration changes. Theme/API URL persist across launches. Model output becomes Compose text/spans, never executable HTML or a WebView. The native Markdown subset supports headings, bold, standalone HTTP(S) links and colored code fences; unsupported markup remains literal. Unknown source languages remain plain text.

Saving uses Android's document picker. Sharing grants temporary access only to a generated cache file through a non-exported FileProvider; no broad storage permission is needed. Source and answer copying use the native clipboard.

## API contract and lifecycle

GET health exposes only status and api_key_configured. POST query accepts JSON {prompt} and returns result, num_turns, duration_ms, total_cost_usd and is_error. POST stream accepts the same JSON and emits UTF-8 SSE message events containing safe SystemMessage/AssistantMessage/UserMessage/ResultMessage/OtherMessage projections, then done. These are SDK messages rather than token deltas.

Invalid JSON/media/Origin/body size/prompt receives safe 400/415/403/413/422 before inference; the actual body limit is 32 KiB and the strict trimmed prompt range is 1–4000 characters. Missing key gives query 503 or SSE error. Provider failure gives query 502 or SSE error without done. Exact safe details survive the native boundary; unexpected malformed responses receive a generic message.

SDK built-in tools, autoapprovals, settings, skills, MCP, plugins and persisted sessions are disabled. The key enters this run's subprocess options without process-wide mutation. Only allowlisted text/subtype/model/result metrics leave the server; thinking, raw environment/config, paths, tool arguments and session IDs are excluded. Exact key occurrences are redacted.

Node heartbeat/deadline/disconnect handling cleans up timers and closes SDK iterators. OkHttp callbacks work off the main thread, and coroutine cancellation cancels the call. Native SSE parsing supports buffered UTF-8, LF/CRLF, comments and multiline data, and requires final result plus done. Error results remain errors after transport completion.

The lesson generator checks all 16 SVG/PlantUML labels against actual source markers and creates both Android assets and public download copies. All diagram presentation attributes are explicit, including fill=none on alternative frames, preventing the previous black-frame/missing-arrow issue.

Before exposing this paid-inference server publicly, add HTTPS, authentication and cost/rate controls. See **VERIFICATION.md** for checks actually run and native/provider limitations, **ASSETS.md** for provenance, and **BUILD-PROMPT.txt** for the reusable specification.
