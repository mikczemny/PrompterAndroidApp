# CLAUDE.md

Wskazówki dla Claude Code przy pracy nad tym repozytorium.

> Ten plik nadpisuje `CLAUDE.md` z katalogu nadrzędnego (`Claude Folder/`), który
> opisuje **inny projekt** (grę „Stack Drop"). Jeśli w kontekście sesji pojawiają
> się instrukcje o canvasie, `stack-drop/` albo `script.js` — nie dotyczą tego repo.

## Czym jest projekt

**Prompter** — natywna androidowa aplikacja teleprompterska (Kotlin + Jetpack
Compose), która słucha mówcy i przewija skrypt w jego tempie. Rozpoznawanie mowy
działa w 100% na urządzeniu ([Vosk](https://alphacephei.com/vosk/)): bez klucza
API, bez konta, żadne audio nie opuszcza telefonu. Docelowo płatna aplikacja na
wielu rynkach językowych.

Pełny opis architektury: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
Stan przed wydaniem i lista zadań: [PRZEGLAD-I-PLAN-WYDANIA.md](PRZEGLAD-I-PLAN-WYDANIA.md).

## Build — uwaga, ścieżki spoza ASCII łamią Android Gradle Plugin

Android Gradle Plugin odrzuca build twardym błędem, jeśli ścieżka repo zawiera
znaki spoza ASCII (np. `G:\Mój dysk\...`, `E:\Gitówa\...`):

```
Your project path contains non-ASCII characters.
```

To nie jest ostrzeżenie — `assembleDebug`/`testDebugUnitTest` przerywają się na
etapie aplikowania pluginu, zanim cokolwiek się skompiluje. Dwie drogi:

**1. Budować z kopii na ścieżce czysto ASCII** (PowerShell):

```powershell
robocopy "G:\Mój dysk\Claude Folder\1. Software\PrompterApp" "$env:TEMP\prompter-build" /MIR /XD ".git" "src_extracted" "build" /XF "*.apk" /NFL /NDL /NJH /NJS /NP
```

potem w kopii:

```bash
./gradlew assembleDebug
```

APK ląduje w `app/build/outputs/apk/debug/app-debug.apk`.

**2. `android.overridePathCheck=true` w `gradle.properties`** — ominięcie
sprawdzenia zamiast kopiowania repo. Prostsze, ale bywa kruche na Windowsie przy
niektórych wersjach AGP/Gradle. **Ta opcja jest już włączona w tym repo**
(`gradle.properties`) — na maszynie, gdzie ten wpis został dodany, build i
`./gradlew testDebugUnitTest` działają wprost z głównej ścieżki repo bez
kopiowania. Jeśli na innej maszynie mimo to pojawi się powyższy błąd, kopia
(opcja 1) jest pewniejsza.

## Narzędzia — nie zawsze są w PATH

Android SDK bywa poza PATH (np. `C:\Android\sdk` na jednej z maszyn). Wołać po
pełnych ścieżkach, gdy `adb`/`aapt2`/`dexdump` nie są rozpoznawane bezpośrednio:

- `platform-tools/adb.exe`
- `build-tools/<wersja>/aapt2.exe`
- `build-tools/<wersja>/dexdump.exe`

Wgranie APK na telefon:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Jeśli `adb` zgłasza `INSTALL_FAILED_VERSION_DOWNGRADE`, na telefonie jest nowsza
wersja niż budowana — **nie odinstalowywać na ślepo**, bo skasuje to zapisane
skrypty użytkownika z `ScriptStore` (przechowywane jako zwykłe pliki w pamięci
wewnętrznej aplikacji, bez backupu — patrz `docs/ARCHITECTURE.md` §7). Najpierw
ustalić, skąd wzięła się ta wersja.

## Praca na dwóch maszynach

Projekt jest rozwijany na dwóch komputerach. Repo:
`github.com/mikczemny/PrompterAndroidApp` (prywatne).

Zdarzyło się już (2026-07-22), że praca z jednej maszyny była zacommitowana
lokalnie, ale nigdy niewypchnięta — `git status` na drugiej wyglądał czysto,
a na GitHubie brakowało całego dnia pracy. **Na starcie sesji sprawdzić:**

```bash
git ls-remote origin
```

To autorytatywny stan po stronie serwera. Rozjazd `versionCode` między telefonem
a repo to najszybszy sygnał, że gdzieś wisi niezpushowana praca:

```bash
adb shell dumpsys package com.mikczemny.prompter | grep versionCode
```

Zdarza się też, że dwie sesje Claude Code są otwarte równolegle na tym samym
katalogu repo (np. dwa okna tej samej pracy) — jeśli `git status` pokazuje
zmiany, których bieżąca sesja nie wprowadziła, sprawdzić aktywne sesje przed
edytowaniem/commitowaniem dalej, żeby nie nadpisać się nawzajem na tym samym
working tree/indeksie gita.

## Konwencje w kodzie

- Komentarze tłumaczą **dlaczego**, nie **co** — trzymać ten poziom, jest
  konsekwentnie utrzymany w całym repo.
- Komentarze i nazwy po angielsku, także komunikaty błędów widoczne dla
  użytkownika (UI jest angielskie).
- `match/` to **czysty Kotlin bez zależności od Androida** — to celowe i warto
  tego pilnować: jest kandydatem na wspólny moduł Kotlin Multiplatform pod iOS.
  Nie wciągać tam `android.*`.
- Compose: stan trzymany w `remember` w composable'ach, bez ViewModeli. Przy
  rozbudowie rozważyć ViewModel, ale nie mieszać obu podejść w jednym ekranie.
- Callbacki z `VoskSpeechRecognizer` (rozpoznawanie, status modelu, stan
  słuchania) przychodzą z wątków tła, nie z wątku głównego — w `TeleprompterScreen`
  są opakowywane w `Handler(Looper.getMainLooper()).post { }`, zanim dotkną
  stanu Compose. Nowe callbacki dodawane w tym miejscu powinny trzymać się tej
  samej zasady.
- Ekran teleprompteru jest zawsze jasny-na-czarnym, niezależnie od motywu
  systemu (stałe `STAGE_*` w `TeleprompterScreen.kt`).

## Czego brakuje (świadomie, nie przypadkiem)

- **Brak CI** — nie ma `.github/workflows`. Przy pracy na dwóch maszynach CI ma
  dodatkową wartość poza samym buildem: wymusza push.
- **Stringi UI zahardkodowane** w composable'ach; `strings.xml` ma tylko
  `app_name`. Lokalizacja wymaga refaktoru (P1-1 w `PRZEGLAD-I-PLAN-WYDANIA.md`).
- **Testy jednostkowe `match/`/`speech/`/`data/`/`document/` już istnieją**
  (`app/src/test`) — utrzymać ten poziom pokrycia przy zmianach w tych pakietach,
  nie tylko w UI.

Pełna, priorytetyzowana lista otwartych spraw: [PRZEGLAD-I-PLAN-WYDANIA.md](PRZEGLAD-I-PLAN-WYDANIA.md).
