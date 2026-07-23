# Prompter — dokumentacja techniczna

Stan na 2026-07-22.

> **Skąd pochodzi ta dokumentacja.** Sekcje §1–§5 i §7 opisują kod z `main`
> (`f0d68f0`) — przeczytany w źródłach, opis jest wiarygodny. Sekcja §6 opisuje
> funkcje dodane na stacjonarnym w v1.1.0 (import `.docx`, zapis skryptów), do
> których **nie było dostępu do źródeł** — zrekonstruowano je z dezasemblacji APK.
> Struktura się zgadza, ale szczegóły implementacyjne trzeba potwierdzić po
> pushu. Miejsca niepewne są oznaczone „⚠ do potwierdzenia".
>
> **Aktualizacja 2026-07-23:** źródła `document/` i `data/` są teraz dostępne na
> `main` — opis w §6 potwierdzony zgodny z kodem. P0-1 (wyścig Start/Stop, patrz
> §4.3) jest naprawiony.

---

## 1. Idea produktu

Zwykły teleprompter przewija tekst ze stałą prędkością i to mówca musi się do
niego dostosować. Prompter odwraca zależność: **słucha, rozpoznaje, gdzie jesteś
w skrypcie, i przewija w Twoim tempie**. Można zwolnić, przyspieszyć, zrobić
pauzę — tekst czeka i rusza, gdy tylko podejmiesz mówienie.

Kluczowa decyzja produktowa: rozpoznawanie działa **offline, na urządzeniu**.
Żadne audio nie opuszcza telefonu. To jednocześnie argument sprzedażowy
(prywatność), techniczny (brak opóźnień sieciowych, działa bez zasięgu) i
biznesowy (brak kosztów zmiennych na użytkownika).

## 2. Mapa modułów

```
com.mikczemny.prompter
├── MainActivity.kt — punkt wejścia, przełącznik Home ↔ Teleprompter
├── match/ — silnik synchronizacji (czysty Kotlin, przenośny)
│   ├── TextMatcher.kt — tokenizacja, normalizacja, alignment
│   └── ScriptMatcher.kt — stanowy tracker pozycji w skrypcie
├── speech/ — rozpoznawanie mowy (Android + Vosk)
│   ├── Language.kt — rejestr języków i URL-i modeli
│   ├── VoskModelManager.kt — pobieranie, weryfikacja i cache modeli
│   └── VoskSpeechRecognizer.kt — ciągłe rozpoznawanie, wyniki częściowe i finalne
├── document/ — import dokumentów (v1.1.0)
├── data/ — trwałe przechowywanie skryptów (v1.1.0)
└── ui/ — Jetpack Compose
    ├── HomeScreen.kt — wybór skryptu i języka
    ├── TeleprompterScreen.kt — scena czytania, sterowanie, pętla przewijania
    └── theme/Theme.kt
```

Podział jest celowy: **`match/` nie ma ani jednego importu `android.*`**. Cała
wartość produktu — algorytm śledzenia — jest przenośna i testowalna na zwykłej
JVM. Jedyna zależność spoza `kotlin.*` to `java.text.Normalizer`.

## 3. Silnik synchronizacji (`match/`)

To jest serce aplikacji. Warto zrozumieć, dlaczego wygląda tak, a nie inaczej.

### 3.1 Problem

Rozpoznawanie mowy zwraca transkrypcję **niedokładną**: gubi słowa, przekręca je,
dokleja „yyy" i „no więc". Naiwne szukanie podciągu („znajdź w skrypcie ostatnie
rozpoznane słowa") zatrzymuje się na pierwszej rozbieżności i prompter zamarza.

### 3.2 Rozwiązanie: lokalne dopasowanie sekwencji

`alignToScript()` (`TextMatcher.kt`) traktuje dwie rzeczy jako sekwencje:

- **spoken** — ostatnie rozpoznane słowa (bufor 8 słów),
- **scriptWindow** — okno skryptu wokół bieżącej pozycji.

i uruchamia wariant **Smitha-Watermana** (lokalne dopasowanie), który toleruje
podstawienia i luki po obu stronach. Zwraca najlepiej punktowane dopasowanie albo
`null`, jeśli nic nie przekroczyło progu zaufania.

Punktacja (stałe w `TextMatcher.kt`):

| Stała | Wartość | Znaczenie |
|---|---|---|
| `MATCH_SIM_THRESHOLD` | 0.6 | poniżej tego podobieństwa słowa to niedopasowanie |
| `MATCH_SCORE` | 8.0 | nagroda za trafienie, skalowana podobieństwem |
| `MISMATCH_PENALTY` | −4.0 | kara za niedopasowanie |
| `GAP_PENALTY` | −3.0 | kara za lukę (pominięte/wtrącone słowo) |
| `MIN_ACCEPT_SCORE` | 10.0 | poniżej tego wyniku nie ufamy dopasowaniu |

Dodatkowo dopasowanie jest karane za odległość od oczekiwanej pozycji mówcy
(`DISTANCE_PENALTY`), a zwracany wynik niesie też liczbę faktycznie dopasowanych
słów — pojedyncze przypadkowe trafienie daleko w skrypcie nie wystarcza już, by
przesunąć wskaźnik (patrz §3.4 i `PRZEGLAD-I-PLAN-WYDANIA.md`, poprawki z
2026-07-22/23).

Podobieństwo pojedynczych słów liczy `wordSimilarity()` — odległość
Levenshteina znormalizowana długością, więc „prompter" vs „promter" = 0.875
(trafienie), a „prompter" vs „samochód" ≈ 0.1 (odrzucone).

### 3.3 Normalizacja i tokenizacja

`normalizeWord()` sprowadza słowo do postaci porównywalnej: małe litery,
rozkład NFKD, **usunięcie znaków diakrytycznych**, usunięcie wszystkiego, co nie
jest literą lub cyfrą Unicode. Dzięki temu „zażółć" i „zazolc" to to samo słowo —
istotne, bo rozpoznawanie bywa niekonsekwentne w diakrytykach.

`splitWords()` rozwiązuje problem, o który potyka się większość implementacji:
**chiński i japoński nie mają spacji między słowami**. Funkcja dzieli tekst na
spacjach, ale każdy znak CJK/kany traktuje jako osobny token. Dzięki temu
alignment ma tę samą ziarnistość niezależnie od języka. Zakresy Unicode w
`isCjk()` (`TextMatcher.kt`).

### 3.4 Śledzenie pozycji (`ScriptMatcher`)

Klasa stanowa, karmiona kolejnymi fragmentami transkrypcji przez
`pushTranscript()`. Kluczowe decyzje:

- **Okno wyszukiwania** to nie cały skrypt, tylko okno wokół bieżącej pozycji
  (`LOOKBACK_WORDS`, `LOOKAHEAD_WORDS`). Ogranicza koszt i, ważniejsze,
  zapobiega fałszywym trafieniom w odległych, podobnie brzmiących fragmentach.
- **Wskaźnik nie cofa się bez powodu.** Krótki krok naprzód nie wymaga
  dodatkowego dowodu; duży skok naprzód albo jakiekolwiek cofnięcie musi być
  poparte dłuższą, czystą frazą (`STRONG_MATCH_WORDS`, `STRONG_JUMP_SCORE`) —
  inaczej pojedyncze przypadkowe słowo teleportowałoby prompter po skrypcie.
  Naprawione 2026-07-22/23 — był to najpoważniejszy zgłoszony błąd trackingu.
- **Częściowe transkrypcje Voska** przychodzą kumulatywnie (cała dotychczasowa
  wypowiedź przy każdym callbacku, nie tylko nowa końcówka) i bywają
  rewidowane, nie tylko rozszerzane. `pushTranscript` porównuje z poprzednią
  częściową hipotezą i dokłada tylko faktycznie nowy fragment, więc bufor nie
  zapycha się powtórzeniami tych samych słów.
- **Pauza** to brak postępu przez `PAUSE_MS` = 1100 ms → przewijanie staje.
- **Prędkość** (słowa/s) liczona z historii ostatniego postępu i wygładzana
  EMA (`SPEED_SMOOTHING` = 0.15), żeby nie skakała między słowami.

`getState()` zwraca `MatchState`: indeks bieżącego słowa, łączną liczbę, flagę
pauzy, prędkość i postęp 0–1.

Pokrycie testami: `app/src/test/.../match/ScriptMatcherTest.kt` — JVM, bez
emulatora, obejmuje właśnie te przypadki (pojedyncze przypadkowe słowo, czysty
skok naprzód, korektę wsteczną, częściowe transkrypcje kumulatywne i rewidowane).

## 4. Rozpoznawanie mowy (`speech/`)

### 4.1 Model języka jest pobierany, nie wbudowany

W APK **nie ma żadnego modelu**. Przy pierwszym użyciu danego języka
`VoskModelManager` pobiera jego model „small" (~30–50 MB) z oficjalnego
repozytorium Alpha Cephei do pamięci wewnętrznej aplikacji i od tej pory język
działa w pełni offline. To utrzymuje rozmiar aplikacji w ryzach niezależnie od
liczby obsługiwanych rynków.

Wspierane języki (`Language.kt`): angielski, hiszpański, chiński, polski,
francuski, niemiecki, włoski, portugalski, rosyjski, hindi, japoński. Dodanie
rynku = dopisanie jednego wpisu.

### 4.2 Ścieżka pobrania i jej zabezpieczenia

`ensureModel()` jest `@Synchronized` i blokujące — woła się je z wątku tła.
Kolejność: sprawdź, czy to już aktualnie załadowany język → sprawdź katalog na
dysku → pobierz → zweryfikuj → rozpakuj → zweryfikuj strukturę → podmień
atomowo → załaduj → zwolnij poprzedni model, jeśli był inny język.

Zabezpieczenia w `downloadAndUnpack()`/`unzipStrippingTopFolder()`:

- **wymuszone HTTPS** i odrzucenie przekierowania poza `https`,
- **SHA-256** liczone strumieniowo w trakcie pobierania; jeśli `Language.sha256`
  jest ustawione, niezgodność przerywa instalację
  (⚠ obecnie wszystkie wpisy mają `null` — mechanizm gotowy, sumy do uzupełnienia),
- **ochrona przed zip-slip** — ścieżka każdego wpisu jest kanonizowana i musi
  zawierać się w katalogu docelowym,
- **limity** liczby wpisów i rozmiaru po rozpakowaniu (obrona przed zip-bombą),
- **walidacja struktury** — rozpakowane archiwum musi zawierać katalog `conf/`,
  inaczej nie jest uznawane za model Vosk i jest odrzucane przed załadowaniem,
- **podmiana atomowa** — rozpakowanie do `<kod>.tmp`, potem `renameTo`, więc
  przerwane pobranie nie zostawia połowicznego modelu.

Katalog docelowy: `filesDir/models/<kod-języka>/`. Obecność `conf/` w środku jest
używana jako znacznik „model gotowy".

**Zarządzanie pamięcią:** menedżer trzyma tylko **jeden** aktywny model
naraz (`current: Pair<kod, Model>?`). Przy zmianie języka poprzedni `Model`
jest zamykany (`Model.close()`) dopiero po tym, jak nowy model załaduje się
poprawnie — błąd ładowania nowego języka nie zostawia więc menedżera bez
żadnego działającego modelu. Naprawione 2026-07-23 (P0-3); wcześniej modele
każdego języka, jaki kiedykolwiek został użyty w danej sesji, zostawały
załadowane w pamięci natywnej na stałe.

### 4.3 Ciągłe rozpoznawanie

`VoskSpeechRecognizer` opakowuje Voskowy `SpeechService` i emituje
`(text, isFinal, timestampMs)` zarówno dla wyników **częściowych**, jak i
**finalnych**. Częściowe są tu ważniejsze niż finalne: pozwalają reagować
w trakcie wypowiadania frazy, a nie po jej zakończeniu — to różnica między
promptem, który nadąża, a takim, który się spóźnia o zdanie.

Próbkowanie: 16 kHz (`SAMPLE_RATE`).

Flaga `cancelRequested` obsługuje przypadek „użytkownik kliknął Stop, gdy model
jeszcze się ładował" — bez niej rozpoznawanie startowałoby po fakcie. Start i
stop dzielą jeden `lifecycleLock`: sprawdzenie `cancelRequested`, publikacja
`speechService`/`recognizer` i `service.startListening()` dzieją się jako jeden
atomowy krok, więc kliknięcie Stop nie może wylądować w oknie między „decyzją
o starcie" a „mikrofon faktycznie włączony" — albo widzi już w pełni żywy
serwis i go zatrzymuje, albo ląduje pierwsze i wątek startu przerywa się, zanim
`startListening()` w ogóle się wykona. `Recognizer` jest też jawnie zamykany
(`close()`) w `stop()` i na ścieżce przerwanego startu, więc cykl Start/Stop nie
wycieka pamięci natywnej. Naprawione 2026-07-23 — było to P0-1 (wyścig
prowadzący do włączonego mikrofonu przy UI pokazującym stan wyłączony) i część
P0-3 (wyciek `Recognizer`) z `PRZEGLAD-I-PLAN-WYDANIA.md`.

Callbacki (`onResult`, `onError`, `onListeningChanged`, `onModelStatus`)
przychodzą z wątku tła (`Thread{}` w `start()`) albo — dla wyników rozpoznawania
— z wątku, na którym Vosk woła `RecognitionListener` (samo `SpeechService`
odsyła je już przez `Handler(Looper.getMainLooper())`, ale status modelu i stan
"słucham" nie przechodziły przez tę ścieżkę). `TeleprompterScreen` teraz
opakowuje każdy z tych callbacków w `Handler(Looper.getMainLooper()).post { }`,
zanim dotknie stanu Compose. Naprawione 2026-07-23 — było to P0-2.

## 5. Warstwa UI (`ui/`)

### 5.1 Nawigacja

Bez biblioteki nawigacyjnej — `MainActivity` trzyma `Session?` w stanie Compose.
`null` → `HomeScreen`, nie-`null` → `TeleprompterScreen`. Wystarczające przy
dwóch ekranach; przy trzecim warto przejść na Navigation Compose.

### 5.2 Scena czytania

Ekran teleprompteru jest **zawsze jasny-na-czarnym**, niezależnie od motywu
systemu (stałe `STAGE_BG`, `STAGE_FG` itd.). Powód praktyczny: w jasnym motywie
prawie biały tekst „przed nami" był niewidoczny.

Kolorowanie słów w `buildAnnotatedString`:
- przeczytane → szary (`READ_COLOR`),
- bieżące → zielony, pogrubione (`CURRENT_COLOR`),
- nadchodzące → jasne (`UPCOMING_COLOR`).

Stała linia prowadząca na **40% wysokości** ekranu wyznacza „czytaj tutaj".
Dotknięcie dowolnego słowa przenosi tam wskaźnik śledzenia (tap-to-jump) — to
najszybszy sposób odzyskania pozycji po fałszywym dopasowaniu albo ustawienia
się do powtórki. Osobny przycisk resetuje do początku skryptu.

### 5.3 Pętla przewijania

Najciekawszy fragment UI. Działa w `withFrameNanos`, czyli synchronicznie z
klatkami, i **łączy dwa sygnały**:

1. **prędkość** — `wordsPerSecond` z matchera przeliczone na piksele/s
   (przez średnią wysokość słowa), z clampem na maksymalną prędkość,
2. **korekcję pozycji** — różnicę między miejscem, gdzie bieżące słowo powinno
   być (linia 40%), a gdzie faktycznie jest, mnożoną przez współczynnik lerpu.

Samo przewijanie prędkością dryfowałoby; samo skakanie do pozycji szarpałoby.
Połączenie daje płynny ruch, który **sam się leczy z dryfu**.

Pozycje pionowe słów zbiera callback `onTextLayout`, mapując indeks słowa na
`getLineTop()` przez offsety znakowe policzone raz przy budowaniu tekstu (a nie
przez wyszukiwanie podciągu w renderowanym stringu na każdej zmianie układu).

### 5.4 Sterowanie

Dolny pasek: strzałka powrotu, duży przycisk Start/Stop (zielony ↔ czerwony),
restart i zębatka. Ustawienia (czcionka, margines, jasność ekranu, lustro,
pozycja przycisku Left/Center/Right) są schowane za zębatką, żeby nie zaśmiecać
sceny czytania. Pozycja przycisku jest ustawialna, bo przy trzymaniu telefonu w
ręce kciuk sięga w różne miejsca zależnie od ręki.

Aplikacja obsługuje **autoobrót** (`fullSensor`), a dzięki `configChanges` obrót
nie restartuje aktywności — rozpoznawanie przeżywa zmianę orientacji w trakcie
czytania. `ui/ScreenAwake.kt` trzyma ekran włączony i przypięty do wybranej
jasności podczas czytania, zwalniając oba ustawienia po wyjściu ze sceny.

## 6. Import dokumentów i zapis skryptów (v1.1.0)

### `document/DocumentImporter`
Wczytuje dokument wskazany przez użytkownika przez **Storage Access
Framework** (`ContentResolver.openInputStream`, `getType`, `query`) — bez
uprawnień do pamięci. Zwraca zapieczętowany wynik `Outcome.Success` /
`Outcome.Failure`, czyli błędy są modelowane w typie, a nie wyjątkami.

### `document/DocxExtractor`
Wyciąga tekst z `.docx`. `.docx` to ZIP, więc implementacja iteruje wpisy
(`ZipInputStream.getNextEntry`), szuka `word/document.xml` i parsuje go **SAX-em**,
sklejając tekst w `StringBuilder`.

Istotne z punktu widzenia bezpieczeństwa: **nic nie jest zapisywane na dysk** —
w całym pakiecie `document/` nie ma ani jednego wywołania `FileOutputStream`,
`FileWriter` czy metod zapisu `File`. Nie ma więc podatności zip-slip, mimo że
rozpakowywany plik pochodzi od użytkownika. Jest też limit `MAX_CHARS` z flagą
`truncated`, czyli ochrona przed gigantycznym dokumentem. PDF-y są obsługiwane
przez `pdfbox-android`; `TextReflow` sklejа z powrotem linie, które PDF łamie na
marginesie strony, a `ScriptFormatting` potrafi rozbić tekst na jedno zdanie na
linię na życzenie użytkownika.

### `data/ScriptStore`, `data/SavedScript`
Trwałe przechowywanie skryptów jako **zwykłe pliki** (`listFiles`, `mkdirs`,
`delete`, `lastModified`, `isFile`) — jeden plik na skrypt w katalogu
wewnętrznym aplikacji, sortowane po dacie modyfikacji. Bez bazy danych, bez
SharedPreferences.

Konsekwencja praktyczna: **odinstalowanie aplikacji kasuje skrypty użytkownika.**
Dlatego przy `INSTALL_FAILED_VERSION_DOWNGRADE` nie wolno odinstalowywać na
ślepo. Katalog skryptów jest też wyłączony z backupu i transferu D2D (§7) —
to prywatny, niepublikowany tekst użytkownika, nie coś do wysłania do chmury
Google.

## 7. Prywatność i dane

| Dane | Gdzie trafiają |
|---|---|
| Audio z mikrofonu | przetwarzane wyłącznie lokalnie przez Voska, nigdzie nie zapisywane ani nie wysyłane |
| Skrypty użytkownika | pliki w pamięci wewnętrznej aplikacji, nie opuszczają urządzenia |
| Modele językowe | pobierane z `alphacephei.com` po HTTPS, cache w pamięci wewnętrznej |

Sieć jest używana **wyłącznie** przy pierwszym pobraniu modelu danego języka.
Rozpoznawanie nigdy nie wymaga połączenia.

Backup jest **całkowicie wyłączony** (`allowBackup="false"`), więc ani skrypty,
ani modele nie trafiają na serwery Google. Reguły `backup_rules.xml` /
`data_extraction_rules.xml` wykluczają oba katalogi (`models`, `scripts`) także
na wypadek, gdyby `allowBackup` kiedyś wrócił do `true` — nic nie ma się wtedy
po cichu ujawnić.

Uprawnienia: `RECORD_AUDIO` (rozpoznawanie) i `INTERNET` (tylko pobranie modelu).
Nic więcej.

## 8. Konfiguracja techniczna

| | |
|---|---|
| Język / UI | Kotlin, Jetpack Compose (BOM 2025.12.01), Material 3 |
| `minSdk` | 24 (Android 7.0) |
| `compileSdk` / `targetSdk` | 36 |
| JVM target | 17 |
| Rozpoznawanie | `com.alphacephei:vosk-android:0.3.75` (przez JNA) |
| Build | Gradle, AGP przez `com.android.application` |
| Wersja | `version.properties` — `VERSION_NAME`/`VERSION_CODE` jako jedyne źródło prawdy |

Reguły ProGuard dla Voska i JNA są napisane (`app/proguard-rules.pro`); R8 i
shrink-resources są włączone w wariancie `release`. Brak jeszcze
`signingConfig` i podziału po ABI dla wydania na Google Play — patrz
`PRZEGLAD-I-PLAN-WYDANIA.md` §6.

## 9. Skąd się wziął ten kod

Silnik dopasowania został **przeniesiony z prototypu webowego** w Next.js
(`textMatcher.js`, `ScriptMatcher`, hook `useSpeechRecognition`). Prototyp leży
lokalnie w `src_extracted/`, poza buildem i poza gitem. Kontrakty klas
androidowych celowo odzwierciedlają te z JS-a, co ułatwia porównywanie zachowań
przy strojeniu progów.

## 10. Dalsze kroki

Pełna lista z priorytetami: [PRZEGLAD-I-PLAN-WYDANIA.md](../PRZEGLAD-I-PLAN-WYDANIA.md).

Skrótowo, w kolejności wartości:
1. ~~Trzy błędy P0~~ — wyścig przy Stop, zapis stanu Compose z wątków tła,
   wycieki `Model`/`Recognizer`. Naprawione 2026-07-23.
2. Testy jednostkowe `match/` (czysta JVM, bez emulatora) + CI — testy już są
   (`app/src/test`); workflow CI wciąż brakuje.
3. Wyniesienie stringów UI do `strings.xml` — warunek lokalizacji.
4. Weryfikacja licencji modeli Voska pod kątem sprzedaży komercyjnej.
5. Konfiguracja wydania: AAB, podpis, podział ABI (R8 już włączone).
6. iOS — najsensowniejsza droga to wydzielenie `match/` do wspólnego modułu
   Kotlin Multiplatform, patrz §7 przeglądu.
