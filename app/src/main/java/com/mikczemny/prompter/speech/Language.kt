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
    // SHA-256 of the model zip, lower-case hex. Null skips verification — fill
    // in as sums are collected so downloads become checkable end to end.
    val sha256: String? = null,
)

object Languages {

    private const val BASE = "https://alphacephei.com/vosk/models/"

    val ENGLISH = Language(
        code = "en",
        displayName = "English",
        englishName = "English (US/UK)",
        modelUrl = BASE + "vosk-model-small-en-us-0.15.zip",
        approxMb = 40,
        sha256 = "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498",
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
        sha256 = "09b239888f633ef2f0b4e09736e3d9936acfd810bc65d53fad45261762c6511f",
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
        sha256 = "3af8b0e7e0f835ae9d414ce5df580237a3cfb08d586c9fbbb0f7ff29ad5b14ba",
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
        sha256 = "c4cd16498ea544f446f9e9a55cbd602b71cfe5a2b6f2b0834d81e1b6fce15f0d",
        sample = "Cześć, witam Was serdecznie w kolejnym odcinku. Dzisiaj pokażę, jak " +
            "działa prompter sterowany głosem. Kiedy mówię, tekst przewija się w moim " +
            "tempie. Mogę zwolnić, przyspieszyć, a nawet zrobić pauzę, a prompter " +
            "poczeka i ruszy dalej, gdy tylko podejmę mówienie.",
    )

    private val FRENCH = simple("fr", "Français", "French", "vosk-model-small-fr-0.22.zip", 41, "cabf6180e177eb9b3a9a9d43a437bd5e549f3a7d09525e5d69a3fed787be12ad")
    private val GERMAN = simple("de", "Deutsch", "German", "vosk-model-small-de-0.15.zip", 45, "b7e53c90b1f0a38456f4cd62b366ecd58803cd97cd42b06438e2c131713d5e43")
    private val ITALIAN = simple("it", "Italiano", "Italian", "vosk-model-small-it-0.22.zip", 48, "9ec65e75861d1c6c2e457cccd932705340dcdf233f5b239f00733b4de0bf3267")
    private val PORTUGUESE = simple("pt", "Português", "Portuguese", "vosk-model-small-pt-0.3.zip", 31, "6e1ce909032e1afa7a88e68a3d628ecafff302bdf195befab308826c395e93b7")
    private val RUSSIAN = simple("ru", "Русский", "Russian", "vosk-model-small-ru-0.22.zip", 45, "961d5ff98a17f4aa6de69864d0aa71fa5bac682301d2b5d17a3f24c5c99a46d4")
    private val HINDI = simple("hi", "हिन्दी", "Hindi", "vosk-model-small-hi-0.22.zip", 42, "7c50a10866889f0ac21d912c20537a055a597ed09fc1d3e5bcd798f9f0017e48")
    private val JAPANESE = simple("ja", "日本語", "Japanese", "vosk-model-small-ja-0.22.zip", 48, "efa092d280153a77615e9e0c7d7283e93e600de3d19d3bec686c57ef19d52eac")

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
        sha256: String,
    ) = Language(
        code = code,
        displayName = displayName,
        englishName = englishName,
        modelUrl = BASE + modelFile,
        approxMb = approxMb,
        sha256 = sha256,
        sample = ENGLISH.sample, // localized starter scripts can be added later
    )
}
