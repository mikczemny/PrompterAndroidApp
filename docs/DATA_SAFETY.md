# Google Play Data Safety notes

Prepared from the codebase on 2026-08-12. Re-check after adding any SDK or
backend. The final answers are the developer's legal declaration in Play
Console, not an automated result.

## Current code-based position

- User data collected by the developer: **No**.
- User data shared with third parties: **No**.
- Data processed ephemerally/on device: microphone audio for matching; camera
  preview; script/document text.
- User-controlled local files: WAV, MP4 and saved scripts.
- Account creation: **No**; account-deletion requirement does not apply.
- Advertising: **No**.
- Analytics/crash reporting: **No SDK present**.
- Data encrypted in transit: model downloads use HTTPS; user content is not
  transmitted.
- Users can request deletion: not applicable to developer-held data; local
  recordings/scripts can be deleted in app or by uninstalling/deleting files.

## Important interpretation

Google defines "collection" around data transmitted off the user's device.
Local processing alone generally is not collection, but verify the current form
wording and every transitive SDK before submission. Alpha Cephei receives a
normal model-download request (for example IP/user-agent at server level), but
the app does not attach user content or identifiers.

## Permissions to disclose

- `RECORD_AUDIO`: on-device voice tracking and optional WAV recording.
- `CAMERA`: optional front-camera preview and silent MP4 recording.
- `INTERNET`: selected Vosk model download only.

The app now presents a clear in-app explanation immediately before each runtime
camera/microphone permission request. Store listing and privacy policy must use
consistent language.
