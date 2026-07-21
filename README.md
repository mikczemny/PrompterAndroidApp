# Prompter — offline'owy prompter sterowany głosem (Android)

Natywna aplikacja Android (Kotlin + Jetpack Compose), która działa jak
PromptSmart Pro: słucha mówcy i przewija scenariusz w jego tempie. Rozpoznawanie
mowy działa **w 100% offline** na urządzeniu (biblioteka [Vosk](https://alphacephei.com/vosk/)) —
bez klucza API, bez konta, bez wysyłania dźwięku do chmury.

## Jak to działa

- `match/TextMatcher.kt` + `match/ScriptMatcher.kt` — silnik dopasowania. Zamiast
  szukać dokładnego tekstu, dopasowuje "ostatnie kilka rozpoznanych słów" do
  "okna scenariusza" lokalnym dopasowaniem sekwencji (wariant Smitha-Watermana).
  Toleruje przejęzyczenia, pominięte słowa i wtrącenia — pozycja aktualizuje się
  tylko, gdy dopasowanie jest wystarczająco pewne.
- `speech/VoskSpeechRecognizer.kt` — ciągłe rozpoznawanie mowy przez Vosk,
  emituje wyniki częściowe i końcowe.
- `speech/VoskModelManager.kt` — rozpakowuje model językowy do pamięci aplikacji
  przy pierwszym uruchomieniu.
- `ui/TeleprompterScreen.kt` — płynne przewijanie łączące prędkość (słowa/s) z
  korekcją pozycji względem faktycznie wypowiedzianego słowa; kontrolki czcionki,
  marginesu, lustra i mikrofonu.

## Model językowy

Model polski (`vosk-model-small-pl-0.22`, ~50 MB) jest pobierany automatycznie
podczas budowania do `app/src/main/assets/model-pl/` (zadanie Gradle
`downloadVoskModel`) i **nie jest commitowany do repo**. Pierwszy build wymaga
połączenia z internetem tylko po to, by pobrać model — sama aplikacja działa
potem offline.

## Budowanie

Wymagany JDK 17+ oraz Android SDK (platform 34, build-tools 34.0.0).

```bash
./gradlew assembleDebug
```

APK powstanie w `app/build/outputs/apk/debug/`.

Najwygodniej otworzyć projekt w **Android Studio** i uruchomić na fizycznym
telefonie (jakość mikrofonu ma znaczenie dla rozpoznawania mowy).

## Do rozważenia (kolejne kroki)

- Dodanie modelu angielskiego i przełącznika języka (oryginalny prototyp miał
  opcję en-US).
- Zapisywanie sesji (offset czasowy vs. scenariusz) do synchronizacji z montażem.
- Kalibracja progów dopasowania pod konkretny akcent na podstawie nagrań.

---

Prototyp webowy (Next.js), na którym oparto silnik, znajduje się lokalnie w
`src_extracted/` (poza buildem, ignorowany przez git).
