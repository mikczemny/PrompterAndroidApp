# AGENTS.md

Instructions for coding agents working in this repository.

## Product

Prompter is a proprietary Android teleprompter (Kotlin/Jetpack Compose) with
fully on-device Vosk speech tracking. SelfiePrompter combines a floating front
camera, silent MP4 and WAV capture; ExtPrompter starts text-only for a separate
camera. User audio/video/scripts are not uploaded.

Read `README.md`, `docs/ARCHITECTURE.md` and
`docs/PLAY_RELEASE_CHECKLIST.md` before broad changes.

## Session start and Git

The project is used on two machines. Check `git status`, `git fetch` and the
ahead/behind count before editing. Preserve unrelated local work. Do not commit
`.claude/`, build outputs, recordings, model archives, keystores,
`keystore.properties` or `local.properties`.

## Build

JDK 17, Android SDK 36. Primary verification:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug bundleRelease
```

`android.overridePathCheck=true` works around AGP failures on non-ASCII Windows
paths; an ASCII-path copy is the fallback. SDK tools may live under
`C:\Android\sdk`. Never uninstall an existing app to fix a signature/version
error before checking saved scripts/recordings.

## Code rules

- English code/comments; comments explain why. User copy uses `strings.xml`.
- Keep `match/` pure Kotlin and Android-free.
- Speech/model callbacks originate off-main; post before Compose state writes.
- Keep `ScriptMatcher.visibleRange` updated by the UI frame loop.
- CameraX video intentionally has no audio; one AudioRecord stream feeds Vosk
  and WAV to avoid microphone contention.
- Do not unbind/disable the camera while MP4 finalization is active.
- Recording must remain foreground-only and always end in Save/Discard.
- Preserve permission disclosures immediately before runtime permission prompts.
- FileProvider scope must stay limited to the recordings directory; prefer SAF
  over broad storage permissions.
- Add tests for pure Kotlin/data/document/speech changes.

## Release/security invariants

- `version.properties` is the version source; bump code per Play upload.
- Release credentials are gitignored and never printed or committed.
- `allowBackup=false`, HTTPS-only model downloads, archive limits/path checks,
  hardened XML and non-exported providers must not regress.
- Update privacy/Data Safety/release docs when data behavior or SDKs change.
- Model SHA-256 pins are a tracked release gate until all values are populated.
