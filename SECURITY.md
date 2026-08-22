# Security policy

## Supported version

Only the latest version on `main` is supported before public release. After
release, only the current Play production version and the next update will be
supported.

## Reporting

Do not open a public issue for a vulnerability. Contact the repository owner
privately and include affected version, reproduction steps, impact and any
suggested mitigation. Do not include user recordings, scripts, credentials or
model archives containing private data.

## Security model

Prompter processes scripts, microphone audio and camera video locally. It has no
account, analytics, ads or user-content backend. Internet is used to retrieve
Vosk models from Alpha Cephei over HTTPS. The application uses runtime
permissions, non-exported FileProvider access, SAF-scoped storage, disabled
backup, bounded archive extraction and hardened XML parsing.

Every Vosk archive currently referenced by `Language.kt` has a pinned SHA-256
value computed from the official Alpha Cephei download. Run
`scripts/compute-model-checksums.ps1` and update the pin whenever a model URL or
archive changes.
