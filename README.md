# DownWork — Android

Android client for DownWork: describe the software you want by voice or text, get a
proper requirements brief written by AI, edit it, get a quote in credits, submit, and
receive the finished code as a GitHub repository (optionally deployed to your AWS account).

Operator: Raviga Apps Private Limited, India.

## Related repositories

- [downwork-ios](https://github.com/alankrita3/downwork-ios) — iOS client (owns the shared design system)
- [downwork-backend](https://github.com/alankrita3/downwork-backend) — API, AI, email flow (owns the API contract)

## Status

Every screen in the shared design is built: Welcome, Terms, Home, Capture (live dictation
with the ink line), AI consent, Transcript, Drafting, Document (edit, regenerate, versions),
Quote with delivery targets, Connect AWS, Project status with team comments, Delivery with
revisions, Credits, Settings (delivery targets, notifications, recovery key, privacy and data).

The debug build talks to the backend's **dev** stage when `secrets.properties` names it.
Without that file the app runs on a built-in demo backend that implements the same API in
process (with a fake review lifecycle), so every screen can be exercised on any phone.

## Getting started

```bash
git clone https://github.com/alankrita3/downwork-android.git
cd downwork-android
cp secrets.properties.example secrets.properties   # fill in what you have; all keys optional
./gradlew assembleDebug
```

Open the folder in Android Studio (Ladybug or newer, JDK 17+) and run `app` on a device or
emulator with API 26+.

### Configuration (all optional, all gitignored)

| file | what it holds |
|---|---|
| `secrets.properties` | `API_BASE_URL_DEBUG`, `API_BASE_URL`, `REVENUECAT_API_KEY` |
| `app/google-services.json` | Firebase project for push notifications |
| `keystore.properties` | path and passwords of the Play upload key for release builds |

- No `API_BASE_URL*` → the in-app demo backend serves everything.
- No `REVENUECAT_API_KEY` → purchases are unavailable; the demo backend grants credits directly.
- No `google-services.json` → the app builds and runs, but receives no push notifications.
- Debug builds have a "Demo backend" switch in Settings that overrides the URL on next launch.

## Build and test

```bash
./gradlew assembleDebug        # debug APK at app/build/outputs/apk/debug/
./gradlew test                 # unit tests (JVM)
./gradlew assembleRelease      # minified; signed only when keystore.properties exists
```

## Architecture

Kotlin, Jetpack Compose, Material 3, single activity, hand-wired dependencies.

```
com.raviga.downwork
├── data/api        Retrofit interface + DTOs for api-contract.md v0.3, errors, job polling
├── data/demo       In-process demo backend (DemoApi) and its heuristic brief writer
├── data/local      EncryptedSharedPreferences session, DataStore prefs, JSON cache
├── data/repo       Session, Project, Credits repositories (state + cache + API)
├── data/billing    RevenueCat over Google Play Billing
├── data/audio      SpeechRecognizer dictation; MediaRecorder fallback
├── push            FCM service, token registration, notifications
├── di              AppContainer (one per process)
└── ui              theme, components, nav, one package per screen
```

- **Identity**: no sign-in. A stable `installId` registers the device; the token lives in
  EncryptedSharedPreferences; a recovery key moves projects to another phone.
- **Source of truth**: the backend. Repositories cache the last known state on disk so the app
  opens instantly and reads work offline; every write goes through the API.
- **Voice**: live dictation through the platform SpeechRecognizer drives the ink line and the
  on-screen transcript. Devices without it record an AAC file that the backend transcribes.
- **Design**: mirrored from `DownWork iOS/docs/DESIGN.md`; Android notes in `docs/DESIGN.md`.

## Licences

Instrument Serif is used under the SIL Open Font License (`LICENSES/InstrumentSerif-OFL.txt`).
