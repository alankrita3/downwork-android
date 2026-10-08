# DownWork Android — design notes

The design system is owned by iOS: `DownWork iOS/docs/DESIGN.md` ("Wet ink, dry ink").
This file records only how Android mirrors it and where the platform differs.

## Tokens → code

| Design | Android |
|---|---|
| colours (paper, ink, graphite, ash, rule, wash, cobalt, moss, amber, brick) | `ui/theme/Color.kt` (`Ink` object); also mapped onto the Material 3 colour scheme in `Theme.kt` so stock components pick them up |
| type styles (display 44/48, title 34/38, heading 26/30, dictation 30/38, body 17/25, secondary 15/21, button 17/22 medium, caption 13/18) | `ui/theme/Type.kt` (`DwType`), all in sp so font scaling works; `includeFontPadding = false` |
| Instrument Serif | bundled `res/font/instrument_serif_{regular,italic}.ttf` (OFL) |
| System sans | Roboto via `FontFamily.Default` |
| 8pt grid, 20pt gutter, 32pt section gap, 12pt row padding | `ui/theme/Dimens.kt` (`Dw`), in dp |
| buttons 52 tall, radius 12; fields wash radius 10 padding 14; mic 72 circle; status mark 8 | `Dw.buttonHeight`, `Dw.buttonRadius`, `Dw.fieldRadius`, `Dw.fieldPadding`, `Dw.micSize`, `Dw.statusMark` |
| hairline 1px | `Dw.hairline = 1.dp` (Compose draws it at device pixels; on xxhdpi it is 3 px, which reads as a hairline) |

Light appearance only: no `values-night`, `enableEdgeToEdge` with light system bars, no dynamic colour.

## Screens → routes

| Design screen | Route (`ui/nav/Routes.kt`) | Composable |
|---|---|---|
| Welcome | `welcome` | `WelcomeScreen` |
| Terms | `terms` | `TermsScreen` |
| Home | `home` | `HomeScreen` |
| Capture, Transcript, Drafting | `capture/{projectId}?mode=` | `CaptureScreen` (three phases in one screen so the dictation state survives the transition) |
| AIConsent | `ai_consent` | `AiConsentScreen` (returns `ai_consent_granted` through the back stack entry) |
| Document | `document/{projectId}?reveal=` | `DocumentScreen` |
| SectionEditor, Regenerate | `section/{projectId}/{sectionId}` + bottom sheet | `SectionEditorScreen`, `RegenerateSheet` |
| Versions | `versions/{projectId}`, `version/{projectId}/{v}` | `VersionsScreen`, `VersionPreviewScreen` |
| Quote + DeliveryTargets inline | `quote/{projectId}` | `QuoteScreen` |
| ConnectAWS | `connect_aws` | `ConnectAwsScreen` |
| ProjectStatus | `status/{projectId}` | `StatusScreen` |
| Delivery, RevisionRequest | `delivery/{projectId}` + bottom sheet | `DeliveryScreen` |
| Credits | `credits` | `CreditsScreen` |
| Settings and children | `settings`, `settings/targets`, `settings/privacy`, `settings/recovery`, `settings/notifications` | `SettingsScreen`, `SubScreens.kt` |

Status vocabulary (mark colours, row lines, detail titles, push lines) is in `ui/status/StatusCopy.kt`.

## Platform differences

- **Dictation.** Android has no in-process equivalent of `SFSpeechRecognizer` that can run
  alongside a recorder: the platform `SpeechRecognizer` lives in another process and takes the
  microphone. So Capture uses live dictation (restarted across pauses) and sends the text as a
  `voice` input without audio. Devices without a recognizer fall back to `MediaRecorder` → upload
  → backend transcription, matching the iOS path. The ink line is driven by the recognizer's RMS
  callback (about 10 Hz, interpolated) or the recorder's peak amplitude at 20 Hz.
- **Reduce motion.** Read from `ANIMATOR_DURATION_SCALE == 0`: the ink line becomes a flat cobalt
  line that pulses opacity; the document reveal still runs but is a single fade per section.
- **Identity.** No Keychain: the token lives in EncryptedSharedPreferences and is excluded from
  backup, so "Move to another phone" (recovery key) matters more than on iOS. A 401 triggers a
  silent re-register with the same `installId`.
- **Links.** Privacy, terms, the repo and the CloudFormation quick-create URL open in a Chrome
  Custom Tab with a paper toolbar.
- **Notifications.** Asked once on Home (Android 13+). Push data with a `projectId` refreshes that
  project in the foreground; in the background a notification deep-links `downwork://project/{id}`.
- **Fonts.** Instrument Serif is bundled rather than fetched, so the first frame has the right face
  with no Play Services dependency.

## Copy that lives in the app

- Capture hint: "Tell us what it does, who it is for, and anything it must connect to. Ramble is fine."
- Empty home: "No projects yet. Tap the microphone and describe what you want built."
- Offline: "Couldn't reach DownWork. Check your connection and try again."
- Timeline footer: "Projects are usually delivered well ahead of this date." (the quote screen
  shows `config.quote.timelineNote` from the backend instead).
