# Prompter — offline, voice-tracking teleprompter (Android)

A native Android app (Kotlin + Jetpack Compose) that works like PromptSmart Pro:
it listens to the speaker and scrolls the script at their pace. Speech
recognition runs **100% on-device** ([Vosk](https://alphacephei.com/vosk/)) — no
API key, no account, no audio ever sent to the cloud.

Built to be commercialized as a simple paid app across multiple markets
(English US/UK, Spanish, Chinese, and more).

## How it works

- `match/TextMatcher.kt` + `match/ScriptMatcher.kt` — the matching engine.
  Instead of exact string search it aligns "the last few recognized words"
  against "a window of the script" using local sequence alignment (a
  Smith-Waterman variant). It tolerates misspoken words, skipped words and
  filler; the position only advances when the match is confident. Tokenization
  is Unicode-aware and falls back to character-level units for CJK/kana.
- `speech/Language.kt` — registry of supported languages; add a market by
  appending one entry.
- `speech/VoskModelManager.kt` — downloads a language's model on demand (once)
  into app-internal storage, then loads it. After that the language is fully
  offline.
- `speech/VoskSpeechRecognizer.kt` — continuous on-device recognition, emitting
  partial and final results.
- `ui/HomeScreen.kt` — script entry and language picker, with word count,
  estimated speaking time and the script-length limits.
- `ui/TeleprompterScreen.kt` — the reading stage: a focus band that lights the
  line being read and dims the rest, velocity+correction smooth scroll,
  tap-a-word-to-jump, restart for retakes, 3-2-1 countdown, mic-permission flow
  and the first-run model-download overlay. The script renders as one immutable
  string with the highlight painted over it, so tracking a word repaints without
  re-measuring the text.
- `ui/ScreenAwake.kt` — holds the screen on, pins its brightness, and hides the
  system bars while prompting.

## Languages & models

No models are bundled in the APK (keeps it small). On first use of a language,
its Vosk "small" model (~30–50 MB) is downloaded from the official Alpha Cephei
repo and cached on the device. Currently offered: English, Spanish, Chinese,
Polish, French, German, Italian, Portuguese, Russian, Hindi, Japanese.

Only the **first** use of each language needs internet; recognition then works
offline.

## Building

Requires JDK 17 and the Android SDK (platform 36, build-tools 36.0.0).

```bash
./gradlew assembleDebug
```

APK lands in `app/build/outputs/apk/debug/`. Run on a physical phone rather than
an emulator — microphone quality is the whole point of on-device STT.

The matching engine has no Android dependencies, so its behaviour is covered by
plain JVM tests:

```bash
./gradlew testDebugUnitTest
```

The project path must be pure ASCII: the Android Gradle Plugin refuses to build
from a directory containing non-ASCII characters on Windows.

### Versioning

`version.properties` at the repo root is the single source of truth. The build
reads it, and the app shows `BuildConfig.VERSION_NAME` on the home screen, so
what is displayed can never drift from what was built. Bump `VERSION_CODE` for
every upload to Play.

### Release builds

Release is minified and resource-shrunk by R8. Vosk reaches its native library
reflectively through JNA, so `app/proguard-rules.pro` keeps those classes by
hand — a release build that skips them installs fine and then dies the moment
recognition starts. Always smoke-test a signed release on a device before
shipping; unit tests will not catch this class of failure.

## Security notes

The downloaded model archive is the app's only untrusted input, and it is
unpacked and then handed to native code. `VoskModelManager` therefore requires
HTTPS (including after redirects), rejects archive entries whose paths escape
the target directory, and caps the unpacked size and entry count. Models are
excluded from cloud backup and device transfer — they are large and freely
re-downloadable.

## Roadmap toward release

- Localize the app's own UI chrome per market (currently English).
- Persist the last-used language; optionally pre-select the device locale.
- Verify the model archives by checksum, so a compromised host cannot swap them.
- CJK (Chinese/Japanese) matching works at character level but benefits from
  real-world tuning of the alignment thresholds per script.
- Play Store: signing config, Play Asset Delivery vs. current on-demand
  download, and a paid-app / in-app-purchase setup.
- Optional: per-language starter scripts, session save (time offset vs. script)
  for video-edit sync.

---

The original Next.js web prototype the engine was ported from lives locally in
`src_extracted/` (outside the build, git-ignored).
