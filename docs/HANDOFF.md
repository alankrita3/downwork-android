# DownWork Android — handoff: what only the founder can plug in

Everything below is a placeholder today. The app builds, runs and demos without any of it: with no
backend URL it uses the in-app demo backend, and the debug build already points at the backend's dev
stage. Each item says where the value goes. Backend and iOS keep their own lists; items marked (shared)
are the same credential used by more than one repo.

## Accounts and keys

| # | Item | Where it goes | Status |
|---|---|---|---|
| 1 | **Play Console app** for `com.raviga.downwork` (Raviga Apps Private Limited), name "DownWork" | Play Console → Create app | not created |
| 2 | **Four consumable in-app products**: `credits_10`, `credits_25`, `credits_50`, `credits_100` with INR prices (₹9,999 / ₹24,999 / ₹49,999 / ₹99,999 proposed; the backend's `/config` carries the credit amounts) (shared with the App Store) | Play Console → Monetise → In-app products | not created |
| 3 | **RevenueCat** project with the Android app, the Google public SDK key `goog_…`, Play service-account credentials, and the webhook URL + shared secret from the backend (shared) | `secrets.properties` → `REVENUECAT_API_KEY`; backend SSM | missing |
| 4 | **Firebase project** with the Android app `com.raviga.downwork` (and `com.raviga.downwork.debug` for debug builds) added; download `google-services.json`; give the backend the service-account JSON for FCM (shared) | `app/google-services.json` (gitignored) | missing |
| 5 | **Upload key** for Play App Signing: a keystore plus `keystore.properties` with `storeFile`, `storePassword`, `keyAlias`, `keyPassword` | `keystore.properties` (gitignored) | missing; release builds are unsigned until then |
| 6 | **Production API URL** once the backend deploys prod | `secrets.properties` → `API_BASE_URL` | placeholder |
| 7 | **Legal pages** hosted at the URLs the backend config returns (currently `https://downwork.in/terms`, `/privacy`), with the version dates used in `/config` (shared) | backend `/config`; the app only opens them | missing |
| 8 | **Grievance officer** name, email and address, company registered address, response window (shared; DPDP) | backend `/config` → `legal.grievance`, `legal.companyAddress` | placeholders |
| 9 | **Play Data safety form** and the microphone / notifications declarations; the app collects voice (processed by OpenAI), project text, device identifier, purchase history | Play Console → App content | to fill |
| 10 | **AWS role policy** for `DownWorkDeployRole` (currently AdministratorAccess per the backend contract); the Connect AWS screen describes the role in plain words | backend CloudFormation template | to decide |

## Verified against the backend dev stage (2026-10-09)

The whole lifecycle ran from the Android app on an emulator against the real dev API: register →
terms → typed description → AI consent → generate (real OpenAI draft, job polling) → the rendered brief →
quote (93 credits, Standard) → submit (document locked, team emailed) → team comment → edit Summary (version 2)
→ re-quote (95) → resubmit (2-credit difference charged) → approved → team comment → delivered → revision
requested → delivered again → AWS done → accepted. Also: versions list and preview, credits ledger, delivery
targets, AWS connect (external ID issued), settings, privacy screen. All 31 of the iOS session's captured dev
responses decode with the Android models (`app/src/test/resources/fixtures`).

Not exercised end to end: voice capture (the emulator has no speech recogniser; on a real phone the platform
`SpeechRecognizer` drives live dictation and the ink line), store purchases (no RevenueCat key), and push
(no `google-services.json`). The code paths exist and compile; they need the items above.

## Decisions I made that you may want to change

- Voice on Android uses the platform speech recogniser for live dictation (words appear as you speak) and
  sends the text; devices without it record audio for the backend to transcribe. iOS does both at once.
- 1 credit = ₹1,000; brackets micro/starter/standard/pro/enterprise; two included revision rounds; audio
  kept 30 days; quotes valid 14 days; auto-accept 14 days after delivery. All of these live in the backend
  `/config`; the app renders whatever it is sent.
- Notifications permission is asked once on the home screen (Android 13+). Change in `HomeScreen.kt`.
- Light appearance only (white background per your brief). Instrument Serif (open licence) for the
  document voice, Roboto for everything else.
- `minSdk 26` (Android 8.0, covers ~97% of Indian Android devices); `targetSdk 36` as Play requires.

## Risks to know before store review

- **Google Play's payments policy** (like App Store guideline 3.1.3(e)): in-app billing is meant for digital
  content used inside the app. DownWork sells a real-world service delivered outside the app. Review may
  push back. Options: position credits as a prepaid digital balance consumed in-app for AI drafting and
  quoting (true today), or move project payment to an external invoice. Decide before submission; no code
  depends on it beyond the Credits screen copy.
- The microphone is only used while the Capture screen is listening; the Data safety form must say voice is
  sent to OpenAI for transcription and drafting.

## How to run it tomorrow

1. Open the folder in Android Studio, or from a terminal: `./gradlew assembleDebug`.
2. Plug in the OnePlus (or start the `DownWork_API34` emulator; the `Pixel_6_API_34` one has a pattern
   lock) and press Run, or `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
3. The debug build talks to the backend's dev stage (`secrets.properties`, already on this Mac). Drafting
   uses OpenAI; credits on dev need the backend's admin flow until RevenueCat is connected.
4. For a self-contained demo with no network: Settings → "Demo backend (debug)" on, restart the app. Then
   Describe a project → "Use a sample description" (or speak) → the brief writes itself → Get a quote → Buy
   credits (instant) → Submit → Settings → Demo controls → "Advance the latest project" to play the team's
   moves (comment, approve, deliver) → Accept.
