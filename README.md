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
- `ui/` — `HomeScreen` (script + language picker), `TeleprompterScreen` (word
  highlighting, velocity+correction smooth scroll, font/margin/mirror controls,
  mic-permission flow, first-run model-download overlay).

## Languages & models

No models are bundled in the APK (keeps it small). On first use of a language,
its Vosk "small" model (~30–50 MB) is downloaded from the official Alpha Cephei
repo and cached on the device. Currently offered: English, Spanish, Chinese,
Polish, French, German, Italian, Portuguese, Russian, Hindi, Japanese.

Only the **first** use of each language needs internet; recognition then works
offline.

## Building

Requires JDK 17+ and the Android SDK (platform 34, build-tools 34.0.0).

```bash
./gradlew assembleDebug
```

APK lands in `app/build/outputs/apk/debug/`. Easiest path for development is to
open the project in **Android Studio** and run on a physical phone (mic quality
matters for on-device STT).

## Roadmap toward release

- Localize the app's own UI chrome per market (currently English).
- Persist the last-used language; optionally pre-select the device locale.
- CJK (Chinese/Japanese) matching works at character level but benefits from
  real-world tuning of the alignment thresholds per script.
- Play Store: signing config, Play Asset Delivery vs. current on-demand
  download, and a paid-app / in-app-purchase setup.
- Optional: per-language starter scripts, session save (time offset vs. script)
  for video-edit sync.

---

The original Next.js web prototype the engine was ported from lives locally in
`src_extracted/` (outside the build, git-ignored).
