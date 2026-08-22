# Google Play release checklist

Current as of 2026-08-12.

## Automated/code gates

- [x] `targetSdk`/`compileSdk` 36 (Android 16).
- [x] App Bundle with ABI, density and language splits.
- [x] R8 minification and resource shrinking.
- [x] Upload signing configuration reads only gitignored credentials.
- [x] 16 KB-compatible Vosk native libraries.
- [x] CI debug build and JVM tests.
- [x] Runtime camera/microphone permissions with in-app disclosures.
- [x] Foreground-only recording and explicit Save/Discard.
- [x] Backup disabled; scoped provider/SAF storage; cleartext disabled.
- [x] Open-source attribution and Apache 2.0 text in app.
- [x] Pin SHA-256 for every downloadable Vosk model (`Language.sha256`).
- [ ] Resolve all `lintDebug`/`lintRelease` errors and review warnings.
- [ ] Build and install a signed release AAB-derived APK on physical devices.
- [ ] Test low-memory device/tablet, rotation, interruptions and no-network flow.

## Credentials and Play Console (owner action)

- [ ] Generate and securely back up the upload keystore and passwords.
- [ ] Create `keystore.properties` from the example (never commit it).
- [ ] Enable Play App Signing.
- [ ] Increase `VERSION_CODE` for each uploaded bundle.
- [ ] Run `./gradlew verifyReleaseSigning bundleRelease` for the upload artifact.
- [ ] Complete Android developer identity/account verification.
- [ ] Configure merchant/tax details if distributed as a paid app.

## Privacy and policy

- [ ] Fill contact fields in `docs/PRIVACY_POLICY.md`.
- [ ] Publish privacy policy at a stable public HTTPS URL.
- [ ] Add the URL in Play Console and expose it in-app before production.
- [ ] Complete Data Safety using `docs/DATA_SAFETY.md`; re-scan SDKs first.
- [ ] Complete content rating, target audience and ads declarations.
- [ ] Confirm no Families-policy positioning unless intentionally supported.

## Store assets and testing

- [ ] Final app name/package/price/territories.
- [ ] 512×512 store icon and 1024×500 feature graphic.
- [ ] Phone screenshots; tablet screenshots if tablet distribution is enabled.
- [ ] Short/full descriptions and translations for target markets.
- [ ] Internal test track and Play pre-launch report.
- [ ] Closed testing requirement for the specific developer account, if shown by
      Play Console, completed before production access.
- [ ] Production release notes and rollback/support plan.

## Current hard blockers to production

1. Upload key/Play account setup (intentionally deferred by owner).
2. Public privacy-policy URL and contact details.
3. Final signed-release device test and Play pre-launch report.
4. Store listing assets and declarations.

Google's target API policy states that from 31 August 2026 new mobile apps and
updates must target Android 16/API 36 or higher; this project already does.
Always re-check Play Console immediately before upload because policy dates and
account-specific testing requirements can change.
