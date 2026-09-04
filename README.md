# Prompter for Android

Offline, voice-following teleprompter built with Kotlin and Jetpack Compose.
Prompter listens on-device with Vosk, follows the speaker through a script, and
scrolls at their pace. No account, cloud speech API, advertising SDK or
analytics SDK is used.

## Recording modes

- **SelfiePrompter** uses one device for prompting and recording. It shows a
  draggable, pinch-resizable front-camera preview, keeps text clear of the
  preview, records a silent MP4, and records the microphone once as a high
  quality WAV while the same stream feeds Vosk.
- **ExtPrompter** starts text-only for use with a separate camera. The camera
  button remains available as an optional preview. Voice tracking and WAV
  recording work as in SelfiePrompter.

After Stop, the user explicitly keeps or discards the take. Recordings can be
listed, played/opened, deleted, and saved either in the app folder or a folder
chosen through Android's Storage Access Framework.

The save destination is mandatory on first launch and can be changed later from
the home or Recordings screen. Active prompter and remote-camera screens keep
their device awake so a normal display timeout cannot interrupt a take.

## Main capabilities

- fully on-device speech recognition after a language model is downloaded;
- fuzzy Smith-Waterman-style script alignment tolerant of missed and inserted
  words, with Unicode/CJK-aware tokenization;
- visible-word-constrained matching, pause detection, tap-to-jump and reset;
- highlighted last recognized word and smooth corrective scrolling;
- script library and PDF, DOCX, TXT and Markdown import;
- automatic local script drafts, restoration of the latest text and optional
  names with a localized date/time fallback;
- 11 Vosk languages downloaded on demand;
- camera/microphone disclosures, audio-focus interruption handling and
  foreground-only recording;
- open-source attribution screen with the Apache 2.0 text bundled offline.

## Project structure

- `match/` — pure Kotlin matching engine (no Android dependencies).
- `speech/` — Vosk model management, AudioRecord capture, resampling and WAV.
- `document/` — safe document import and text formatting.
- `data/` — script and recording stores.
- `ui/` — Compose screens, CameraX preview/capture and reading stage.
- `docs/ARCHITECTURE.md` — current technical design.
- `docs/PLAY_RELEASE_CHECKLIST.md` — release gates and manual Play tasks.
- `docs/PRIVACY_POLICY.md` — privacy-policy draft to publish under a public URL.
- `docs/DATA_SAFETY.md` — Play Console declaration notes.

## Build and test

Requirements: JDK 17 and Android SDK platform/build-tools 36.

```bash
./gradlew testDebugUnitTest assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

```bash
./gradlew lintDebug bundleRelease
```

Release AAB: `app/build/outputs/bundle/release/app-release.aab`. It is unsigned
unless a gitignored `keystore.properties` exists; see
`keystore.properties.example`. Never commit the upload keystore or passwords.
For an actual Play artifact, run `./gradlew verifyReleaseSigning bundleRelease`;
the verification task fails rather than allowing an unsigned bundle to be
mistaken for a publishable one.

On Windows, Android Gradle Plugin can reject non-ASCII project paths. The repo
sets `android.overridePathCheck=true`; if a tool still fails, build from a copy
under a plain ASCII path.

## Versioning and release

`version.properties` is the single source of truth. Increase `VERSION_CODE` for
every Play upload. Release builds use R8/resource shrinking and App Bundle ABI,
density and language splits. Always smoke-test a release build on a physical
device: JVM tests cannot exercise Vosk/JNA, CameraX or the microphone.

## Privacy and security

Audio, video, scripts and imported document text stay on the device unless the
user explicitly chooses a destination folder or shares/opens a recording with
another app. The app does not upload user content. Internet access is used only
to download selected Vosk models over HTTPS.

Model downloads enforce HTTPS, reject unsafe redirects, defend against
zip-slip/zip bombs, validate model structure and support SHA-256 pinning.
Backup is disabled. FileProvider exposes only the app recording directory and
uses temporary read grants. DOCX XML parsing disables external entities.

See `SECURITY.md` for reporting and `docs/PLAY_RELEASE_CHECKLIST.md` for known
release gates. The source code is proprietary; see `LICENSE`.
