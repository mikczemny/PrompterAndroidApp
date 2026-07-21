import java.net.URL
import java.util.zip.ZipInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mikczemny.prompter"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mikczemny.prompter"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("com.alphacephei:vosk-android:0.3.47")
}

// ---------------------------------------------------------------------------
// Offline speech model: fetched once at build time into assets, kept out of git.
// ---------------------------------------------------------------------------
val voskModelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-pl-0.22.zip"
val voskModelDir = layout.projectDirectory.dir("src/main/assets/model-pl")

val downloadVoskModel by tasks.registering {
    description = "Downloads the Vosk Polish small model into assets if missing."
    val markerFile = voskModelDir.file("README").asFile
    outputs.dir(voskModelDir)
    onlyIf { !markerFile.exists() }
    doLast {
        val destRoot = voskModelDir.asFile
        destRoot.mkdirs()
        val tmpZip = File.createTempFile("vosk-model", ".zip")
        logger.lifecycle("Downloading Vosk model from $voskModelUrl ...")
        URL(voskModelUrl).openStream().use { input ->
            tmpZip.outputStream().use { out -> input.copyTo(out) }
        }
        logger.lifecycle("Unpacking model into $destRoot ...")
        ZipInputStream(tmpZip.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                // Strip the top-level "vosk-model-small-pl-0.22/" folder.
                val relPath = entry.name.substringAfter('/', "")
                if (relPath.isNotEmpty()) {
                    val outFile = File(destRoot, relPath)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile.mkdirs()
                        outFile.outputStream().use { out -> zip.copyTo(out) }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        tmpZip.delete()
        logger.lifecycle("Vosk model ready.")
    }
}

tasks.named("preBuild") {
    dependsOn(downloadVoskModel)
}
