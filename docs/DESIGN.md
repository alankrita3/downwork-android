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
| Describe chooser (Speak / Type / Upload a document) | bottom sheet on Home and Document | `DescribeChooserSheet` |
| Capture, Transcript / "Here's what we read", Drafting | `capture/{projectId}?mode=&tab=` | `CaptureScreen` (three phases in one screen so the dictation state survives the transition; tabs Speak, Type, Upload) |
| Sensitive-data check | bottom sheet on the review step | `SensitiveSheet` in `CaptureScreen`, patterns in `data/screening/SensitiveScan.kt` |
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
  silent re-register with the same `installId`; if that returns a different client (this phone was
  revoked), the app starts over and Home offers the recovery key.
- **Uploads.** The system document picker (`OpenDocument`, filtered to `config.limits.acceptedFileTypes`
  plus `text/*`); the file is copied to cache, sized, typed by MIME or extension, uploaded to the
  presigned URL and deleted locally. Choosing "Upload a document" in the chooser opens the picker
  straight away.
- **Links.** Privacy, terms, the repo and the CloudFormation quick-create URL open in a Chrome
  Custom Tab with a paper toolbar.
- **Notifications.** Asked once on Home (Android 13+). Push data with a `projectId` refreshes that
  project in the foreground; in the background FCM shows the notification and its tap carries
  `projectId` as an intent extra, which opens the project's own screen (brief, status or delivery).
- **Fonts.** Instrument Serif is bundled rather than fetched, so the first frame has the right face
  with no Play Services dependency.

## Copy that lives in the app

- Capture hint: "Tell us what it does, who it is for, and anything it must connect to. Ramble is fine."
- Empty home: "No projects yet. Tap the microphone and describe what you want built."
- Chooser: "Describe your project" / "Add to <title>"; rows Speak ("Talk it through in English or
  Hindi. We write it up."), Type ("Write or paste a description."), Upload a document ("A PDF, Word or
  text file you already have."); footer "Recordings are deleted after N days and documents as soon as
  they're read. Only the text is kept, for your brief." (N from `config.retention.audioDays`).
- Upload review: "Here's what we read", "From <file>, N pages", "Only the text is kept. The file is
  deleted as soon as it's read, and is never shared with the team.", then the server's `notice`.
- Sensitive data: "This looks like it includes <up to two kinds, then 'and other secrets'>." /
  "We never need passwords, keys or ID numbers to build your project, and it's safer not to share
  them." Buttons "Remove them", "Keep as is", "Edit".
- Refused project: "We can't take this project on", the server's reason, "Nothing has been charged.
  If you think this is a mistake, write to <supportEmail>." Policy refusals elsewhere append the same
  "If you think this is a mistake…" line to the server message.
- Offline: "Couldn't reach DownWork. Check your connection and try again."
- Timeline footer: "Projects are usually delivered well ahead of this date." (the quote screen
  shows `config.quote.timelineNote` from the backend instead).
