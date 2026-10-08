# DownWork — Android

Kotlin + Jetpack Compose client for DownWork. Built alongside two peer Claude sessions:
the iOS app (folder "DownWork iOS") and the backend (folder "DownWork Backend").

## Working agreement (from the founder)

- Do not stop to ask for permission; get the job done. Use placeholders for anything that
  needs her (credentials, accounts, decisions) and keep building.
- Coordinate directly with the peer sessions. The backend owns the API contract; iOS owns the
  shared design system. Mirror both; do not fork local copies.
- `Mirakee-*`, `AstroRaye-*` and `mirakee` folders are reference only: read, never modify.
  Never change AWS resources that are not `downwork-*`.

## Sources of truth

- API: `DownWork Backend/docs/api-contract.md` (canonical; v0.3 at the time of writing).
  Wire shapes live in `data/api/Dtos.kt` and must track it exactly (camelCase keys,
  snake_case enums, `202 {job}` for job starts, bare `Job` from `GET /jobs/{id}`).
- Design: `DownWork iOS/docs/DESIGN.md` ("Wet ink, dry ink"). Tokens in `ui/theme`,
  status vocabulary in `ui/status/StatusCopy.kt`, Android notes in `docs/DESIGN.md`.

## Conventions

- Light appearance only. Paper background, ink text, cobalt is the only accent. No cards,
  shadows or gradients; hairline rows; 8dp grid; 20dp gutter; 52dp buttons, radius 12.
- Instrument Serif (bundled, OFL) for display/title/heading/dictation and figures; Roboto for
  everything else, including document bodies.
- Sentence case everywhere; buttons say what happens ("Submit and pay 48 credits").
- Errors branch on `ApiException.code`, never on message text; copy lives in `StatusCopy.errorLine`.
- Components in `ui/components` are the vocabulary: `PrimaryButton`, `SecondaryButton`,
  `TertiaryButton`, `InlineAction`, `DwTextField`, `PlainEditor`, `DwRow`, `DwTopBar`,
  `BottomBar`, `ScreenScaffold`, `BodyText`, `SectionHeading`, `InkLine`, `TimelineView`.
  Reach for these before writing new chrome.
- ViewModels take `AppContainer` and expose one `StateFlow<State>`; screens are thin.

## Running

```bash
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.raviga.downwork.debug/com.raviga.downwork.MainActivity
./gradlew test
```

- `secrets.properties` (gitignored) holds `API_BASE_URL_DEBUG`; without it the app uses the
  in-process demo backend (`data/demo/DemoApi.kt`). Debug Settings has a switch to force demo.
- Emulator: `ANDROID_SDK_ROOT=~/Library/Android/sdk ~/Library/Android/sdk/emulator/emulator -avd Pixel_6_API_34`
  (the shell's `ANDROID_HOME` points at an old SDK bundle; override it). Prefer a connected
  device when one is attached.
- Package `com.raviga.downwork`; debug builds use `com.raviga.downwork.debug`.
