# Prompter — stan przed wydaniem

Aktualizacja: 2026-08-12.

Ten plik jest krótkim indeksem. Aktualna checklista publikacji znajduje się w
`docs/PLAY_RELEASE_CHECKLIST.md`, architektura w `docs/ARCHITECTURE.md`, a historia
zmian w `CHANGELOG.md`.

## Stan produktu

Gotowe są: offline voice tracking, import i biblioteka skryptów, 11 języków,
SelfiePrompter/ExtPrompter, pływający podgląd kamery, równoległe WAV+MP4,
Save/Discard, biblioteka nagrań, lokalizacja stringów, licencje open source,
audio focus, zabezpieczenia importu/modeli, CI oraz AAB/release config.

GUI nadal będzie rozwijane, ale bieżący kod jest funkcjonalnym kandydatem do
testów przedwydaniowych.

## Blokery produkcji

- prawdziwy upload keystore i konfiguracja konta Play;
- publiczny URL polityki prywatności z danymi kontaktowymi;
- finalny lint/release/signed-device smoke test;
- listing, grafiki, formularze Play i test track/pre-launch report.

## Najbliższa kolejność

1. Domknąć automatyczny audyt, lint i finalny release build.
2. Kontynuować GUI bez naruszania przepływów uprawnień/nagrywania.
3. Przy publikacji: keystore, publiczna polityka, AAB, internal testing.
