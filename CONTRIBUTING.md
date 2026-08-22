# Contributing

This is a proprietary project. Contributions require the owner's approval.

## Setup

- JDK 17
- Android SDK platform/build-tools 36
- Physical Android device for microphone/camera verification

Keep the repository path ASCII-only on Windows when possible. Do not commit
`local.properties`, `keystore.properties`, keystores, APKs, AABs, model ZIPs or
user recordings.

## Required checks

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug bundleRelease
```

For speech/camera changes, also install on a physical device and verify:

- model download and offline restart;
- Start/Stop and last-word tracking;
- interruption/background stop;
- SelfiePrompter preview, pinch, drag and paired MP4/WAV;
- Save/Discard, recordings list and selected-folder output;
- ExtPrompter starts text-only;
- Android Back follows screen history.

## Code conventions

- Comments explain why, not what; code and comments are English.
- Keep `match/` free of Android dependencies.
- Post recognizer/model callbacks to the main thread before Compose state writes.
- Keep `ScriptMatcher.visibleRange` updated by the UI frame loop.
- Add JVM tests for changes in pure Kotlin/data/document/speech logic.
- User-facing copy belongs in `strings.xml`.

## Git

Check the authoritative remote before work because the project is used on two
machines. Preserve unrelated local changes. Use focused commits and increase
`VERSION_CODE` for each Play upload.
