# Prompter — przegląd developerski i plan wydania

Sporządzone: 2026-07-22, na podstawie stanu repo `f0d68f0` (+ trzy niezacommitowane
poprawki bezpieczeństwa) oraz analizy APK v1.1.0 wyciągniętego z telefonu.

## Status na 2026-07-23

Ten dzień domknął większość §2/§3 poniżej:

- **Trzy poprawki bezpieczeństwa** (zip-slip, integralność pobrania, backup) —
  zacommitowane. Zip-slip i wymuszone HTTPS/redirect były już na `main`; SHA-256
  (z opcjonalnym pinem), walidacja że rozpakowany archiwum to model Vosk,
  `allowBackup=false` i wykluczenie katalogu skryptów z transferu D2D dołożone
  dzisiaj.
- **P0-1, P0-2, P0-3 — naprawione.** Szczegóły w §2 poniżej, przy każdym punkcie.
- **Licencje modeli (§8) — zweryfikowane.** Wszystkie 11 modeli to Apache 2.0.
  Szczegóły i rekomendacja w [docs/MODEL-LICENSES.md](docs/MODEL-LICENSES.md).
- **Cztery funkcje z analizy open-prompter** — zacommitowane, w kolejności
  wartości: filtrowanie dysfluencji ASR przed dopasowaniem, pasek postępu
  czytania, ograniczenie okna dopasowania do widocznego fragmentu ekranu,
  poszerzanie okna po >5s ciszy. Szczegóły w nowym §10 poniżej. Homofoniczne
  zwijanie (z tej samej analizy) świadomie odłożone na później.
- **Dokumentacja** — ten plik, `docs/ARCHITECTURE.md`, `docs/MODEL-LICENSES.md`
  i `CLAUDE.md` scommitowane.
- **Nadal otwarte:** `Language.sha256` jest `null` dla wszystkich 11 języków —
  mechanizm działa, ale nie pinuje niczego, dopóki ktoś nie policzy prawdziwych
  sum (Alpha Cephei ich nie publikuje). Ekran „Licencje open source" (wymagany
  teraz, gdy §8 jest zweryfikowane — patrz §10). Stringi UI (P1-1), audio focus
  (P1-2), signing/AAB/podział ABI (§6) — nietknięte.

Reszta tego dokumentu to oryginalny przegląd z 2026-07-22, zachowany bez zmian
poza odnotowaniem statusu przy poszczególnych punktach — kontekst i uzasadnienia
są nadal aktualne.

---

## 0. Zakres przeglądu — co widziałem, a czego nie

To jest istotne dla wiarygodności poniższych ocen:

| Element | Status przeglądu |
|---|---|
| Kod na `main` (`f0d68f0`, ~1400 linii Kotlina) | ✅ przeczytany w całości |
| Moje 3 poprawki bezpieczeństwa | ✅ napisane, skompilowane, **niezacommitowane** |
| Praca ze stacjonarnego (v1.1.0) | ⚠️ **nieprzejrzana** — tylko nazwy klas z dex-a |

Ze stacjonarnego doszły klasy, których nie mogłem ocenić: `data/ScriptStore`,
`data/SavedScript`, `document/DocumentImporter`, `document/DocxExtractor`,
`document/ScriptFormattingKt`, `document/TextReflowKt`, `ui/ScreenAwakeKt`.
**Po pushu trzeba je przejrzeć osobno** — parsowanie `.docx` i I/O plików to
klasycznie miejsca na błędy.

**Aktualizacja (2026-07-22, wieczór):** podejrzenie zip-slipa w `DocxExtractor`
zostało **sprawdzone przez dezasemblację APK i odrzucone**. Pakiet `document/`
nie zapisuje żadnych plików (zero `FileOutputStream`/`FileWriter`/zapisów `File`),
ZIP jest czytany wyłącznie strumieniowo w poszukiwaniu `word/document.xml`, a
zawartość parsowana SAX-em do `StringBuilder` z limitem `MAX_CHARS`. Wejście idzie
przez SAF (`ContentResolver.openInputStream`). Ta ścieżka jest czysta — **nie
trzeba na nią jutro poświęcać czasu.**

**Aktualizacja (2026-07-23):** źródła są teraz dostępne na `main` — powyższa
rekonstrukcja z dex-a potwierdzona zgodna z kodem dla całego pakietu `document/`
i `data/`.

---

## 1. Ocena ogólna

Kod jest **wyraźnie powyżej średniej** dla projektu na tym etapie. Konkretnie:

- Silnik dopasowania (`match/`) jest czysty, czysto funkcyjny, bez zależności od
  Androida, dobrze skomentowany *dlaczego*, a nie *co*. Wybór Smitha-Watermana
  zamiast naiwnego wyszukiwania podciągu jest trafny i faktycznie rozwiązuje
  problem szumu w transkrypcji.
- Tokenizacja jest świadomie Unicode-owa z osobną ścieżką dla CJK/kany — to
  rzecz, o której większość implementacji zapomina i wykłada się na chińskim.
- Pętla przewijania (velocity + korekcja pozycji z lerpem) to właściwe podejście —
  samo się leczy z dryfu zamiast skakać.
- Podział na warstwy (`match` / `speech` / `ui`) jest sensowny i gotowy na
  wieloplatformowość (patrz §6 w `docs/ARCHITECTURE.md`).

Główne słabości to nie architektura, tylko **brak obudowy inżynierskiej**: zero
testów, zero CI, wszystkie stringi zahardkodowane, brak konfiguracji release.
(2026-07-23: testy jednostkowe `match/` już są dodane — patrz §5.)

---

## 2. Błędy i ryzyka w kodzie

Priorytety: **P0** = blokuje wydanie, **P1** = naprawić przed publikacją,
**P2** = dług techniczny.

### P0-1. Wyścig przy Start/Stop — mikrofon może zostać włączony bez możliwości wyłączenia

✅ **Naprawione 2026-07-23.**

`speech/VoskSpeechRecognizer.kt:27` — pole `speechService` **nie było `@Volatile`**,
a było zapisywane z wątku tła (`start()`) i czytane z wątku głównego (`stop()`).

Scenariusz awarii: użytkownik klika Start, model się ładuje. Klika Stop w oknie
pomiędzy sprawdzeniem `cancelRequested` a przypisaniem `speechService`.
`stop()` widzi `null`, ustawia `isListening = false`, UI pokazuje
zielony „Start". Wątek tła kończy pracę i wywołuje `service.startListening()` —
**mikrofon nagrywa, a interfejs twierdzi, że nie**.

To nie było teoretyczne: okno trwa tyle, ile ładowanie modelu z dysku (setki ms).
Dla aplikacji z uprawnieniem `RECORD_AUDIO` to jednocześnie błąd prywatności i
czerwona flaga przy weryfikacji w Google Play.

Naprawa: `speechService` i `recognizer` są teraz `@Volatile`, a `start()`/`stop()`
dzielą jeden `lifecycleLock` — sprawdzenie `cancelRequested`, publikacja obu pól
i `service.startListening()` dzieją się jako jeden atomowy krok, więc Stop nie
może już wylądować w oknie między „decyzją o starcie" a „mikrofon faktycznie
włączony".

### P0-2. Stan Compose zapisywany z wątków tła

✅ **Naprawione 2026-07-23.**

`ui/TeleprompterScreen.kt` — callbacki `onResult`, `onError`,
`onListeningChanged`, `onModelStatus` przychodzą z wątku audio Voska i z
`Thread{}` w `start()`, a zapisują `currentIndex`, `paused`, `isListening`,
`errorMsg`, `modelStatus`.

Zapis `MutableState` spoza wątku głównego nie jest w Compose bezpieczny —
w praktyce objawia się zgubionymi rekompozycjami i sporadycznym zawieszeniem
podświetlenia. Naprawa: każdy z tych czterech callbacków w `TeleprompterScreen`
jest teraz opakowany w `Handler(Looper.getMainLooper()).post { ... }`, zanim
dotknie stanu Compose.

(Doprecyzowanie po analizie: same wyniki rozpoznawania — `onPartialResult`/
`onResult`/`onFinalResult`/`onError` z `RecognitionListener` — Vosk i tak
odsyła już przez własny `Handler(Looper.getMainLooper())` wewnątrz
`SpeechService`. Realny problem dotyczył `onModelStatus` i `onListeningChanged`,
wołanych bezpośrednio z surowego `Thread{}` w `start()`. Opakowanie wszystkich
czterech jest tanie i nie szkodzi, więc zostało tak, jak zaplanowano.)

### P0-3. Wycieki pamięci natywnej — `Model` i `Recognizer` nigdy nie zwalniane

✅ **Naprawione 2026-07-23.**

- `VoskModelManager` trzymał mapę `loaded` i **nigdy nie wołał `Model.close()`**.
  Każdy model to kilkadziesiąt MB pamięci natywnej. Przełączenie kilku języków w
  jednej sesji → OOM na słabszym telefonie.
- `Recognizer` tworzony w `VoskSpeechRecognizer.start()` nie był
  zamykany w `stop()` — wyciek przy każdym cyklu Start/Stop.

Naprawa: `Recognizer.close()` w `stop()` (i na ścieżce przerwanego startu);
`VoskModelManager` trzyma teraz tylko jeden aktywny model (`current: Pair<kod,
Model>?`) i zamyka poprzedni dopiero, gdy nowy załaduje się poprawnie.

### P1-1. Wszystkie stringi UI zahardkodowane — lokalizacja niemożliwa bez refaktoru

`res/values/strings.xml` zawiera **jeden** wpis (`app_name`). Cała reszta to
literały w Kotlinie: `"Start"`, `"Stop"`, `"Paused"`, `"Tracking"`, `"Settings"`,
`"Font"`, `"Margin"`, `"Mirror"`, `"Back to menu"`, komunikaty błędów.

To bezpośrednio kłóci się z pozycjonowaniem z README („commercialized across
multiple markets"). Aplikacja oferuje 11 języków rozpoznawania, ale interfejs
jest wyłącznie angielski. Naprawa jest mechaniczna, ale im później, tym więcej
miejsc. **Zrobić przed pierwszym wydaniem, nie po.** *(Nadal otwarte.)*

### P1-2. Brak obsługi przerwań audio

Nie ma `AudioManager.requestAudioFocus` ani reakcji na przechwycenie mikrofonu.
Telefon w trakcie czytania, asystent głosowy, inna aplikacja — rozpoznawanie
cicho umiera, a UI dalej pokazuje „Tracking". Dla płatnej aplikacji do
nagrywania wideo to scenariusz, który wystąpi u każdego użytkownika. *(Nadal
otwarte.)*

### P1-3. Konfiguracja release nie nadaje się do publikacji

Częściowo zaadresowane: `isMinifyEnabled = true` i `isShrinkResources = true` są
już włączone w wariancie `release` (`app/build.gradle.kts`), z regułami ProGuard
dla Voska/JNA. Nadal brakuje:

- `signingConfig` — nie da się zbudować podpisanego wydania,
- konfiguracji AAB / `bundle { }` z podziałem ABI.

Ostatnie jest ważne: debug APK ma **107 MB**, głównie przez `libvosk.so` i
`libjnidispatch.so` w czterech ABI naraz. AAB z podziałem po ABI daje
użytkownikowi ~30–40 MB zamiast 107. *(Nadal otwarte.)*

### P2-1. Mapowanie pozycji słów jest kosztowne przy długich skryptach

✅ Zaadresowane — `onTextLayout` liczy teraz offsety znakowe raz, przy
budowaniu tekstu, zamiast robić `rendered.indexOf(w, searchStart)` w pętli przy
każdym przeliczeniu układu.

### P2-2. Sztywny `bottom = 400.dp`

`ui/TeleprompterScreen.kt` — dobrane pod jeden ekran. W poziomie i przy
dużej czcionce ostatnie słowa mogą nie dojechać do linii prowadzącej (40%
wysokości), albo zostaje pół ekranu pustki. Powinno być pochodną
`viewportHeight`. *(Nadal otwarte — nie sprawdzone ponownie 2026-07-23.)*

### P2-3. Drobiazgi

- `errorMsg` nigdy sam nie znika — zostaje na ekranie do następnego Startu.
- `LaunchedEffect(Unit)` z `while(true) withFrameNanos` chodzi zawsze, także gdy
  nic nie jest odtwarzane — tanie, ale niepotrzebne.
- `estimateWordsPerSecond()` przy skoku (mówca przeskoczył akapit) daje pik
  prędkości; jest clamp na maksymalną prędkość, więc to tylko szarpnięcie, nie bug.
- Brak `shouldShowRequestPermissionRationale` — po odmowie mikrofonu użytkownik
  dostaje komunikat, ale nie ma ścieżki powrotu do ustawień.

*(Nie sprawdzone ponownie 2026-07-23 — traktować jako wciąż otwarte.)*

---

## 3. Bezpieczeństwo — stan po dzisiejszych poprawkach

✅ **2026-07-23: wszystkie trzy zacommitowane.**

| Co | Gdzie | Status |
|---|---|---|
| Zip-slip przy rozpakowaniu modelu | `VoskModelManager.unzipStrippingTopFolder` | ✅ kanoniczna kontrola ścieżki + limity wpisów/rozmiaru |
| Integralność pobrania | `VoskModelManager.downloadAndUnpack` | ✅ wymuszone HTTPS, blokada redirectu poza https, SHA-256 z opcjonalnym pinem, walidacja że to model Vosk (`conf/`) |
| Backup danych użytkownika | `AndroidManifest.xml` + `res/xml/data_extraction_rules.xml` + `backup_rules.xml` | ✅ `allowBackup=false`, katalogi modeli i skryptów wykluczone też z reguł D2D/cloud na wypadek przywrócenia `allowBackup=true` |

**Zostaje do zrobienia:** pole `Language.sha256` jest na razie `null` dla
wszystkich 11 języków — mechanizm działa, ale nie pinuje niczego, dopóki nie
wpiszemy prawdziwych sum. Alpha Cephei nie publikuje ich obok modeli, więc trzeba
policzyć samodzielnie (pobrać raz, `sha256sum`, wpisać) i traktować jako
zobowiązanie: przy każdej podmianie wersji modelu trzeba zaktualizować hash.

**`DocxExtractor` — sprawdzony, czysty.** `.docx` to ZIP, więc podejrzenie było
uzasadnione, ale implementacja czyta archiwum strumieniowo i nigdy nic nie
zapisuje na dysk, więc zip-slip nie występuje. Szczegóły w
[docs/ARCHITECTURE.md §6](docs/ARCHITECTURE.md).

---

## 4. Dokumentacja

### Co jest dobre
`README.md` jest napisany porządnie — tłumaczy *dlaczego* alignment zamiast
wyszukiwania, opisuje strukturę modułów, ma sekcję o modelach i roadmapę. Lepszy
niż większość README na tym etapie. *(2026-07-23: zaktualizowany o sekcję
security notes zgodną z aktualnym stanem i odhaczony punkt roadmapy o checksumach.)*

### Czego brakuje

1. ~~**`README.md` jest już nieaktualny**~~ — ✅ zaktualizowany 2026-07-23,
   wspomina import `.docx` i zapis skryptów.
2. **Brak `LICENSE`** — dla aplikacji komercyjnej trzeba świadomie zdecydować:
   zamknięte źródło (wtedy nagłówek „All rights reserved") czy otwarte.
   *(Nadal otwarte.)*
3. **Brak `CHANGELOG.md`** — przy dwóch maszynach i wersjach 1.0/1.1.0 to
   przestaje być opcjonalne. *(Nadal otwarte.)*
4. **Brak `CONTRIBUTING` / opisu setupu** — a jest nieoczywisty haczyk:
   **projekt nie zbuduje się w swojej własnej lokalizacji**, jeśli ścieżka
   zawiera znaki spoza ASCII (np. polskie litery) — AGP odrzuca build. Opisane
   teraz w `CLAUDE.md` (patrz niżej), ale nie w samym README. *(Częściowo
   otwarte.)*
5. ~~**`CLAUDE.md` opisuje inny projekt.**~~ — ✅ własny `CLAUDE.md` dodany
   2026-07-23.

---

## 5. Obudowa inżynierska

### Testy — ✅ dodane 2026-07-23

`app/src/test/` istnieje teraz i pokrywa `match/` (`ScriptMatcherTest.kt`,
łącznie z `TextMatcherTest`), `speech/VoskModelManagerTest.kt` (zip-slip i
limity wpisów), `data/ScriptStoreTest.kt` i `document/DocumentTest.kt`.
`match/TextMatcher.kt` i `match/ScriptMatcher.kt` są czystym Kotlinem bez
zależności od Androida — testowalne zwykłym JUnitem, bez emulatora, w
milisekundach.

To jest serce produktu. Jeśli dopasowanie się zepsuje, aplikacja jest
bezwartościowa, a regresję zauważy dopiero użytkownik w trakcie nagrywania.
Zestaw testów pokrywa m.in.: pojedyncze przypadkowe słowo nie przesuwa
wskaźnika, czysty skok naprzód działa, korekta wsteczna działa, częściowe
transkrypcje kumulatywne i rewidowane liczą się poprawnie, `jumpTo`/`reset`
działają i się clampują.

### CI — brak

Nie ma `.github/workflows`. Przy pracy na dwóch maszynach CI ma dodatkową
wartość poza samym buildem: **wymusza push**. Gdyby workflow był, problem
„commit lokalny, nigdy nie wypchnięty" byłby widoczny od razu. *(Nadal
otwarte.)*

Minimalnie: workflow na `push` i `pull_request`, JDK 17, `./gradlew
assembleDebug test`. Uwaga — na runnerze GitHuba ścieżka jest ASCII, więc
problem z niestandardowymi znakami tam nie występuje.

### Skille i konfiguracja Claude Code

W projekcie jest teraz własny `CLAUDE.md` (dodany 2026-07-23) — opisuje
architekturę, pułapkę ścieżki spoza ASCII, i pracę na dwóch maszynach. Skill do
buildu i wgrania na telefon (robocopy → `assembleDebug` → `adb install -r`)
pozostaje do dodania.

---

## 6. Google Play — checklista wydania

### Blokery techniczne
- [ ] **AAB zamiast APK** — nowe aplikacje muszą być publikowane jako App Bundle
- [ ] **`signingConfig`** + keystore do podpisu uploadu, włączone Play App Signing
- [ ] **Podział ABI** — bez tego użytkownik pobiera cztery komplety bibliotek natywnych
- [x] **R8 włączone** — `isMinifyEnabled`/`isShrinkResources` już `true`, reguły ProGuard dla JNA napisane; wciąż wymaga testu na urządzeniu przed wydaniem
- [ ] **`targetSdk`** — Play wymaga API nie starszego niż rok od aktualnego; `targetSdk = 36` na `main` — potwierdzić aktualny wymóg w konsoli, bo próg przesuwa się co sierpień
- [ ] `versionCode` rośnie z każdym uploadem (obecnie 2 w `version.properties`)

### Wymagania formalne
- [ ] **Polityka prywatności pod publicznym URL-em** — obowiązkowa przy
  `RECORD_AUDIO`, nawet gdy nic nie wychodzi z urządzenia. Tu akurat treść
  jest mocna: „nagranie nigdy nie opuszcza telefonu" to argument sprzedażowy,
  nie tylko formalność.
- [ ] **Formularz Data safety** — zadeklarować: audio przetwarzane lokalnie, nie
  zbierane, nie wysyłane. Musi się zgadzać z tym, co robi kod (a `allowBackup=false`
  właśnie pomaga, żeby się zgadzało).
- [ ] **Prominent disclosure** dla mikrofonu — ekran wyjaśniający przed prośbą o
  uprawnienie. Obecnie prośba wyskakuje po kliknięciu Start bez kontekstu.
- [ ] Kwestionariusz klasyfikacji treści
- [ ] Konto sprzedawcy + dane podatkowe (aplikacja płatna)
- [ ] Deklaracja braku reklam

### Materiały do listingu
- [ ] Ikona 512×512, grafika promocyjna 1024×500
- [ ] Zrzuty ekranu — telefon obowiązkowo; tablet, jeśli deklarujesz wsparcie
  (aplikacja obsługuje `fullSensor`, więc tablety wypada wesprzeć)
- [ ] Opis krótki i pełny, **przetłumaczone na rynki docelowe** — listing można
  lokalizować niezależnie od UI, ale UI po angielsku przy polskim opisie
  wygląda niespójnie (patrz P1-1)
- [ ] Wideo promocyjne — dla tego produktu wyjątkowo skuteczne, bo działanie jest
  efektowne dopiero w ruchu

### Przed publikacją
- [ ] Ścieżka testów wewnętrznych + raport przedpremierowy (Play testuje
  automatycznie na kilkunastu urządzeniach — wyłapie ANR-y i crashe, których
  nie zobaczysz na S23)
- [ ] Test na urządzeniu z 3–4 GB RAM — ryzyko OOM z §2 P0-3 jest teraz
  zaadresowane kodowo, ale warto potwierdzić na słabszym sprzęcie
- [ ] Test przy braku sieci na pierwszym uruchomieniu języka (jedyny moment,
  gdy internet jest wymagany)

---

## 7. App Store — to jest osobny produkt, nie port

**Trzeba to powiedzieć wprost: obecny kod nie ma żadnej ścieżki na iOS.**
Kotlin + Jetpack Compose + `vosk-android` (przez JNA) to stos wyłącznie
androidowy. Nie ma tu „przekompilowania" — jest przepisanie.

Realne opcje:

### A. Natywny iOS (SwiftUI) + `SFSpeechRecognizer`
Apple ma własne rozpoznawanie na urządzeniu z flagą `requiresOnDeviceRecognition`.
Zalety: zero pobierania modeli, obsługa modeli systemowych, mniejsza aplikacja.
Wady: **cała logika dopasowania do przepisania w Swifcie**, dwie niezależne
implementacje silnika, które będą się rozjeżdżać.

### B. Kotlin Multiplatform — moim zdaniem właściwy kierunek
Kluczowa obserwacja: **`match/` już jest przenośne**. `TextMatcher.kt` i
`ScriptMatcher.kt` używają wyłącznie `kotlin.*` i `java.text.Normalizer` — to
jedyna zależność do zastąpienia. Silnik, czyli to, co stanowi wartość produktu,
przechodzi na KMP niemal bez zmian.

Podział: `match/` → wspólny moduł KMP, rozpoznawanie mowy → interfejs
`expect/actual` (Vosk na Androidzie, `SFSpeechRecognizer` na iOS), UI → Compose
Multiplatform albo osobno SwiftUI.

**Rekomendacja:** wydać najpierw Androida na Play, a przy okazji wydzielić
`match/` do osobnego modułu Gradle bez zależności androidowych. To kosztuje
niewiele teraz, a jest warunkiem wstępnym KMP później. Decyzję o iOS podjąć po
danych sprzedażowych z Play.

### Specyfika App Store (gdy już dojdzie)
- `NSMicrophoneUsageDescription` i `NSSpeechRecognitionUsageDescription` w
  `Info.plist` — z konkretnym uzasadnieniem, ogólniki są odrzucane
- Pobieranie modeli w locie: Apple pozwala pobierać **dane**, ale nie kod
  wykonywalny. Modele Voska to dane, więc OK — ale bezpieczniej użyć On-Demand
  Resources albo od razu `SFSpeechRecognizer` i problem znika
- Wytyczna 4.2 („minimalna funkcjonalność") — prompter z samym przewijaniem bywa
  odrzucany; śledzenie głosem jest tu wyróżnikiem, warto to podkreślić w nocie
  dla recenzenta

---

## 8. Licencje modeli — sprawdzić przed sprzedażą

✅ **Zweryfikowane 2026-07-23.** Wszystkie 11 modeli "small" wpiętych w
`Language.kt` to **Apache 2.0** — sprawdzone na stronie modeli Alpha Cephei,
skrzyżowane z kartami modeli Hugging Face i plikiem `COPYING` silnika
`vosk-api`. Brak klauzul niekomercyjnych czy share-alike w żadnym z nich. Pełna
tabela i uzasadnienie w [docs/MODEL-LICENSES.md](docs/MODEL-LICENSES.md).

**Rekomendacja: żaden język nie musi zniknąć z listy przed 1.0** — wybór
języków na start powinien wynikać z dopasowania do rynku, nie z ryzyka
licencyjnego. Apache 2.0 wymaga natomiast standardowej atrybucji przy
redystrybucji: ekran „Licencje open source" z notą o Vosk/Alpha Cephei i
linkiem do treści licencji. *(Ekran — nadal otwarte, patrz §10.)*

---

## 9. Proponowana kolejność

~~**Jutro rano, zanim cokolwiek innego (masz ~godzinę):**~~ *(zrobione 2026-07-23:
push, scalenie poprawek bezpieczeństwa, P0-1/P0-2/P0-3, dokumentacja)*

**Następnie, w kolejności wartości:**
1. ~~P0-1, P0-2, P0-3~~ — ✅ zrobione.
2. ~~Testy jednostkowe `match/`~~ — ✅ zrobione. CI wciąż brakuje.
3. ~~Własny `CLAUDE.md` i README z pułapką ścieżki ASCII~~ — ✅ zrobione (README
   nadal bez wzmianki o pułapce ścieżki w samym pliku, patrz §4 pkt 4).
4. Wyniesienie stringów do `strings.xml` (P1-1) — im później, tym drożej.
5. ~~Weryfikacja licencji modeli (§8)~~ — ✅ zrobione, wynik: nic nie trzeba
   usuwać z listy 11 języków.
6. ~~Cztery funkcje z analizy open-prompter (§10)~~ — ✅ zrobione.
7. Konfiguracja release: AAB, podpis, podział ABI (R8 już włączone).
8. Polityka prywatności, Data safety, materiały do listingu.
9. Ścieżka testów wewnętrznych → produkcja.

Punkty 1–3 to solidny fundament i są teraz zrobione. Dopiero potem ma sens
inwestowanie w listing i marketing, bo P0 z §2 zepsułyby pierwsze recenzje.

---

## 10. Funkcje z analizy open-prompter (2026-07-23)

Cztery funkcje z wcześniejszego porównania z open-prompter, zrobione w
kolejności wartości. Homofoniczne zwijanie (piąty punkt z tamtej analizy)
świadomie odłożone jako kandydat na później — nie zaczęte.

1. **Filtrowanie dysfluencji ASR przed dopasowaniem** (`match/TextMatcher.kt`
   `isFillerWord`, użyte w `ScriptMatcher.pushTranscript`). Usuwa tylko czyste
   dźwięki wahania (um, uh, erm i kilka odpowiedników fr/it/de/pl) z *mówionego*
   strumienia przed dopasowaniem. Świadomie nie rusza słów, które bywają
   potoczną wypełniaczem, ale mogą być realną treścią skryptu ("like", "so",
   "well", "eh", "ah", "no").
2. **Pasek postępu czytania** (`ui/TeleprompterScreen.kt`, `ReadingProgressBar`)
   — cienka kreska przypięta do górnej krawędzi, rysowana jako ostatnia warstwa,
   więc zostaje widoczna nawet nad overlayem odliczania/pobierania modelu.
   Napędzana `MatchState.progress`, który matcher już liczył.
3. **Okno dopasowania ograniczone do widocznego fragmentu ekranu**
   (`ScriptMatcher.visibleRange`, aktualizowane co klatkę w
   `TeleprompterScreen` z pozycji scrolla i `wordOffsets`). Słowo, które
   dopasowuje się dobrze, ale jest poza ekranem, nie może wygrać dopasowania
   niezależnie od wyniku — dodatkowe ograniczenie ponad istniejący
   lookahead/lookback w słowach.
4. **Poszerzanie okna po ciszy** (`WIDEN_AFTER_SILENCE_MS` / `WIDE_LOOKAHEAD_WORDS`
   w `ScriptMatcher.tryAlign`) — po >5s bez potwierdzonego postępu okno
   lookahead rośnie z 30 do 200 słów, żeby złapać świadomy skok mówcy (np.
   przewinięcie ręką) bez potrzeby tap-to-jump. Próg "silnego skoku"
   (`STRONG_MATCH_WORDS`/`STRONG_JUMP_SCORE`) zostaje bez zmian, więc to tylko
   poszerza to, co aligner *widzi*, nie to, co jest gotów *zaakceptować*.

13 nowych testów jednostkowych (`FillerWordTest`, `VisibleRangeTest`,
`WidenOnSilenceTest`) w `ScriptMatcherTest.kt`. Przy pisaniu testu dla punktu 4
wykryty i naprawiony błąd we własnym teście: sekwencyjne tokeny testowe typu
"w1".."w60" fałszywie się dopasowywały przez fuzzy-matching (odległość
edycyjna 1 między "w4" i "w46"), zamienione na jednoznacznie odrębne
dwuliterowe tokeny.

**Nadal otwarte z tej analizy:** ekran „Licencje open source" (wymagany teraz,
gdy §8 jest zweryfikowane), homofoniczne zwijanie (odłożone).
