plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    // From Kotlin 2.0 the Compose compiler ships with the Kotlin plugin and is
    // enabled by this plugin rather than a composeOptions version pin.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
