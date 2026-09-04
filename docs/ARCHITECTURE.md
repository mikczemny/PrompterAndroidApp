# Architecture

Current as of 2026-08-12.

## Product flow

`MainActivity` owns the lightweight Compose navigation state:

1. `ModeSelectionScreen` — SelfiePrompter or ExtPrompter.
2. `HomeScreen` — language, script editor/import and script library.
3. `TeleprompterScreen` — prompting, recognition and recording.
4. Auxiliary `RecordingsScreen` and `LicensesScreen`.

Android Back follows that hierarchy. The selected mode is a real preset:
SelfiePrompter opens the front-camera preview; ExtPrompter starts text-only.

## Speech and recording pipeline

`VoskSpeechRecognizer` owns one `AudioRecord` instance. Captured PCM is split:

- full capture rate (prefer 48 kHz, then 44.1/16 kHz) goes to `WavRecorder`;
- `PcmResampler` converts it to 16 kHz for `Recognizer.acceptWaveForm`.

This avoids two clients competing for the microphone. CameraX records a silent
MP4 with the same timestamp. Stop finalizes both files; the user then keeps or
discards the pair. Audio focus stops recognition on interruption, and lifecycle
handling stops recording when the app leaves the foreground.

Callbacks from audio/model threads are posted to the main looper before they
touch Compose state. Start/Stop resource publication is protected by the
recognizer lifecycle lock, and native Vosk resources are explicitly closed.

## Matching

`match/` is pure Kotlin. `TextMatcher` tokenizes Unicode text (character units
for CJK/kana), normalizes it and performs local sequence alignment.
`ScriptMatcher` maintains the confirmed token, speech buffer, confidence and
speed. The UI updates `visibleRange` each frame so matching cannot jump to text
outside the viewport. The current confirmed token is highlighted by drawing
behind one immutable Compose `Text` layout.

## Camera and adaptive layout

One `LifecycleCameraController` is shared by `PreviewView` and video capture.
The front-camera preview is a floating Compose window. A transparent Compose
gesture layer above `PreviewView` consumes pan/pinch so pinch resizes the window
instead of zooming the lens. Window bounds are reported to the reading stage;
the script dynamically reserves the corresponding left or right column.

File output is private cache until Save. Saved recordings go to the app's
external-files `recordings/` directory or a persisted SAF tree selected by the
user. Destination setup is versioned and mandatory before entering the app;
it can be reopened from the home and Recordings screens. `FileProvider`
exposes only the app recording directory to external players.

Both the teleprompter stage and the remote-camera host hold
`FLAG_KEEP_SCREEN_ON` only while visible. This prevents Android's inactivity
timeout from stopping CameraX on a tripod-mounted camera phone.

## Documents and local data

- `ScriptStore`: one private UTF-8 text file per saved script.
- `HomeScreen`: restores the newest script, debounces automatic local saves and
  saves immediately before entering the stage; unnamed scripts use a localized
  date/time title.
- `RecordingStore`: temporary capture, explicit Save/Discard, SAF/app-folder
  listing and deletion, plus cleanup of abandoned cache takes.
- `DocumentImporter`: SAF-only input, PDFBox text extraction, bounded DOCX SAX
  parsing with external entities disabled, and no broad storage permission.

## Models and network boundary

Only `VoskModelManager` needs the network. A selected model is downloaded over
HTTPS into app-private storage, validated and atomically installed. Protections:
HTTPS-only redirects, bounded entries/unpacked bytes, canonical path checks,
structure validation, temporary cleanup and optional SHA-256 pins. Only one
native model remains loaded at once.

## Security and privacy boundaries

- no analytics, ads, accounts or user-content upload;
- `allowBackup=false`; defense-in-depth exclusions cover models, scripts,
  recording preferences and app-owned recordings;
- camera and microphone are runtime permissions preceded by in-app disclosure;
- recording is foreground-only and visible in the stage UI;
- cleartext traffic is disabled;
- launcher activity is the only exported component;
- provider is non-exported and grants read access per intent.

## Tests and CI

JVM tests cover matching, model archive defenses, document parsing, script
storage and PCM resampling. GitHub Actions builds/tests every push and PR.
Before release, run unit tests, lint, debug APK, release AAB and physical-device
smoke tests for Vosk, CameraX and Save/Discard.
