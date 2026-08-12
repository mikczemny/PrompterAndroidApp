import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Version lives in version.properties at the repo root so releasing means
// editing one file, and so the build fails loudly rather than shipping a
// silently wrong version number.
val versionProps = Properties().apply {
    val file = rootProject.file("version.properties")
    require(file.exists()) { "version.properties is missing from the project root" }
    file.inputStream().use { load(it) }
}
val appVersionName: String = versionProps.getProperty("VERSION_NAME")
    ?: error("VERSION_NAME missing from version.properties")
val appVersionCode: Int = versionProps.getProperty("VERSION_CODE")?.toIntOrNull()
    ?: error("VERSION_CODE missing or not a number in version.properties")

// Upload-signing credentials live in keystore.properties at the repo root,
// which is gitignored — the keystore and its passwords must never enter git.
// When the file is absent (CI, a fresh clone, anyone without the upload key)
// the release build simply stays unsigned rather than failing, so assembleDebug
// and CI keep working. See keystore.properties.example for the expected keys.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.mikczemny.prompter"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mikczemny.prompter"
        minSdk = 24
        // Google Play requires a current target for published updates, and
        // targeting 36 opts into the behaviour the app is actually tested on.
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        // Only created when keystore.properties is present; the release build
        // below picks it up via findByName, so its absence leaves the build
        // unsigned instead of breaking.
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // See proguard-rules.pro: Vosk's JNA bridge has to be kept by hand.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // Ship an App Bundle to Play and let it deliver only what each device needs.
    // The four native ABIs of libvosk/libjnidispatch are what make a universal
    // APK ~107 MB; with per-ABI splits a device downloads roughly a third of it.
    bundle {
        abi { enableSplit = true }
        density { enableSplit = true }
        language { enableSplit = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Exposes the version from version.properties to the app itself, so the
        // number on screen can never drift from the number that was built.
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Pinned to the newest AndroidX line that still builds against compileSdk 36.
    // Later releases require compileSdk 37, which Google has not published to the
    // stable SDK channel yet.
    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.activity:activity-compose:1.12.4")

    // Audio-focus compat: gives a single API for requesting/abandoning focus
    // across minSdk 24, used only as an interruption signal for recognition.
    implementation("androidx.media:media:1.7.0")

    // DocumentFile wraps the Storage Access Framework tree the user picks as
    // their recordings folder — list/create/delete without a storage permission.
    implementation("androidx.documentfile:documentfile:1.0.1")

    // CameraX: front-camera preview (and later video capture) for reading to
    // camera. camera-core comes in transitively via camera-camera2.
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    // LocalLifecycleOwner for Compose, needed to bind the camera to the screen.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // 0.3.47 shipped native libraries that are not 16 KB page-size aligned,
    // which Android 15+ devices reject and Play now requires.
    implementation("com.alphacephei:vosk-android:0.3.75")

    // PDF text extraction. Word documents are handled without a library (see
    // DocxExtractor) — only PDF genuinely needs one.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // The matcher is pure Kotlin with no Android dependencies, so its tests run
    // on the JVM — no emulator needed to check tracking behaviour.
    testImplementation("junit:junit:4.13.2")
}

// Language models are downloaded on demand at runtime (see VoskModelManager),
// not bundled — this keeps the APK small and lets us add markets without
// growing the download.
