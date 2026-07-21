package com.mikczemny.prompter.speech

/**
 * A supported recognition language. Each maps to an offline Vosk "small" model
 * that is downloaded on demand (once) rather than bundled — keeps the APK small
 * and lets us add markets by appending a single entry here.
 *
 * Model URLs are the official Alpha Cephei small models; all verified reachable.
 */
data class Language(
    val code: String,          // stable id used for storage + model dir
    val displayName: String,   // shown in the picker (in the language's own name)
    val englishName: String,   // shown as a subtitle
    val modelUrl: String,      // Vosk small model zip
    val approxMb: Int,         // download size hint for the UI
    val sample: String,        // starter script in this language
)

object Languages {

    private const val BASE = "https://alphacephei.com/vosk/models/"

    val ENGLISH = Language(
        code = "en",
        displayName = "English",
        englishName = "English (US/UK)",
        modelUrl = BASE + "vosk-model-small-en-us-0.15.zip",
        approxMb = 40,
        sample = "Hi everyone, welcome back to the channel. Today I'll show you how a " +
            "voice-controlled teleprompter works. As I speak, the text scrolls at my " +
            "own pace. I can slow down, speed up, or even pause for a moment, and the " +
            "prompter simply waits and picks up again the instant I keep talking.",
    )

    val SPANISH = Language(
        code = "es",
        displayName = "Español",
        englishName = "Spanish",
        modelUrl = BASE + "vosk-model-small-es-0.42.zip",
        approxMb = 39,
        sample = "Hola a todos, bienvenidos de nuevo al canal. Hoy os voy a enseñar cómo " +
            "funciona un teleprónter controlado por voz. Mientras hablo, el texto se " +
            "desplaza a mi propio ritmo. Puedo ir más despacio, más rápido, o incluso " +
            "hacer una pausa, y el teleprónter espera y continúa en cuanto sigo hablando.",
    )

    val CHINESE = Language(
        code = "zh",
        displayName = "中文",
        englishName = "Chinese",
        modelUrl = BASE + "vosk-model-small-cn-0.22.zip",
        approxMb = 42,
        sample = "大家好，欢迎回到我的频道。今天我来演示一下语音控制的提词器是如何工作的。" +
            "当我说话时，文字会按照我的节奏滚动。我可以放慢，也可以加快，甚至停顿一下，" +
            "提词器会等待，一旦我继续说话它就会继续滚动。",
    )

    val POLISH = Language(
        code = "pl",
        displayName = "Polski",
        englishName = "Polish",
        modelUrl = BASE + "vosk-model-small-pl-0.22.zip",
        approxMb = 50,
        sample = "Cześć, witam Was serdecznie w kolejnym odcinku. Dzisiaj pokażę, jak " +
            "działa prompter sterowany głosem. Kiedy mówię, tekst przewija się w moim " +
            "tempie. Mogę zwolnić, przyspieszyć, a nawet zrobić pauzę, a prompter " +
            "poczeka i ruszy dalej, gdy tylko podejmę mówienie.",
    )

    private val FRENCH = simple("fr", "Français", "French", "vosk-model-small-fr-0.22.zip", 41)
    private val GERMAN = simple("de", "Deutsch", "German", "vosk-model-small-de-0.15.zip", 45)
    private val ITALIAN = simple("it", "Italiano", "Italian", "vosk-model-small-it-0.22.zip", 48)
    private val PORTUGUESE = simple("pt", "Português", "Portuguese", "vosk-model-small-pt-0.3.zip", 31)
    private val RUSSIAN = simple("ru", "Русский", "Russian", "vosk-model-small-ru-0.22.zip", 45)
    private val HINDI = simple("hi", "हिन्दी", "Hindi", "vosk-model-small-hi-0.22.zip", 42)
    private val JAPANESE = simple("ja", "日本語", "Japanese", "vosk-model-small-ja-0.22.zip", 48)

    /** All languages offered in the picker, primary markets first. */
    val ALL: List<Language> = listOf(
        ENGLISH, SPANISH, CHINESE, POLISH,
        FRENCH, GERMAN, ITALIAN, PORTUGUESE, RUSSIAN, HINDI, JAPANESE,
    )

    val DEFAULT: Language = ENGLISH

    fun byCode(code: String): Language = ALL.firstOrNull { it.code == code } ?: DEFAULT

    private fun simple(
        code: String,
        displayName: String,
        englishName: String,
        modelFile: String,
        approxMb: Int,
    ) = Language(
        code = code,
        displayName = displayName,
        englishName = englishName,
        modelUrl = BASE + modelFile,
        approxMb = approxMb,
        sample = ENGLISH.sample, // localized starter scripts can be added later
    )
}
