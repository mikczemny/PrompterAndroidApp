# Vosk model licenses

Checked 2026-07-23 against [alphacephei.com/vosk/models](https://alphacephei.com/vosk/models),
cross-checked against the Hugging Face model cards (e.g.
[alphacep/vosk-model-small-ru](https://huggingface.co/alphacep/vosk-model-small-ru), tag
`license: apache-2.0`) and the engine's own
[COPYING](https://github.com/alphacep/vosk-api/blob/master/COPYING) file.

All eleven "small" models currently referenced in
[`Language.kt`](../app/src/main/java/com/mikczemny/prompter/speech/Language.kt) are **Apache
License 2.0** — no non-commercial clause, no share-alike, no separate commercial tier:

The same official ZIP files were downloaded on 2026-08-12 and their SHA-256
values pinned in `Language.kt`. `scripts/compute-model-checksums.ps1` reproduces
the calculation whenever a model version changes.

| Language (code) | Model file | Size (matches `approxMb`) | License |
|---|---|---|---|
| English (en) | vosk-model-small-en-us-0.15 | 40M | Apache 2.0 |
| Spanish (es) | vosk-model-small-es-0.42 | 39M | Apache 2.0 |
| Chinese (zh) | vosk-model-small-cn-0.22 | 42M | Apache 2.0 |
| Polish (pl) | vosk-model-small-pl-0.22 | 50M | Apache 2.0 |
| French (fr) | vosk-model-small-fr-0.22 | 41M | Apache 2.0 |
| German (de) | vosk-model-small-de-0.15 | 45M | Apache 2.0 |
| Italian (it) | vosk-model-small-it-0.22 | 48M | Apache 2.0 |
| Portuguese (pt) | vosk-model-small-pt-0.3 | 31M | Apache 2.0 |
| Russian (ru) | vosk-model-small-ru-0.22 | 45M | Apache 2.0 |
| Hindi (hi) | vosk-model-small-hi-0.22 | 42M | Apache 2.0 |
| Japanese (ja) | vosk-model-small-ja-0.22 | 48M | Apache 2.0 |

Matching sizes against the model page confirms these are genuinely the "small" variants
(the page also lists much larger, differently-licensed-in-practice-but-still-Apache "big"
server models for some of these languages, e.g. `vosk-model-ru-0.22` at 1.5G — those are
**not** what this app downloads or ships).

## What Apache 2.0 actually requires of us

Apache 2.0 permits commercial use, redistribution, and modification without royalty. It does
require, for any redistribution of the model (including bundling it or serving it from our own
download endpoint — not applicable today since we point at Alpha Cephei's own URLs, but
relevant if that ever changes):

- Include a copy of the Apache 2.0 license text.
- Retain existing copyright/attribution notices.
- State any modifications made to the licensed material.
- Include a NOTICE file if the original distribution ships one.

None of this blocks a paid app. It does mean the app should carry a licenses/attribution
screen (or an entry in an "About" section) naming Vosk/Alpha Cephei and linking the Apache 2.0
text — standard open-source attribution hygiene, not a blocker for release.

## Recommendation for 1.0

**Ship all 11 languages.** There is no licensing reason to cut any of them — every model in
the current language list clears commercial use under the same permissive terms. Language
selection for 1.0 should be driven by product/market fit and translation-quality concerns (the
Roadmap in the README already flags CJK matching as needing real-world tuning), not by
licensing risk.

If new languages are added later, re-run this same check per model before wiring it in — Alpha
Cephei's page has occasionally hosted community-contributed models under different terms for
niche languages, even though nothing in the current list is affected.
