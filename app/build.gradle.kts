import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/**
 * App languages, derived from the resource folders: English lives in values/, every
 * translation in values-<lang>/strings.xml. Adding such a folder is all a new language needs.
 */
val appLanguages: List<String> = listOf("en") + (file("src/main/res").listFiles() ?: emptyArray())
    .filter { it.isDirectory && Regex("values-[a-z]{2,3}(-r[A-Z]{2})?").matches(it.name) && File(it, "strings.xml").exists() }
    .map { it.name.removePrefix("values-").replace("-r", "-") }
    .sorted()

/**
 * Release signing. The key comes from keystore.properties in the project root (never committed)
 * or from environment variables (for CI):
 *
 *   storeFile=../niimstudio-release.jks      NIIMSTUDIO_STORE_FILE
 *   storePassword=...                        NIIMSTUDIO_STORE_PASSWORD
 *   keyAlias=niimstudio                      NIIMSTUDIO_KEY_ALIAS
 *   keyPassword=...                          NIIMSTUDIO_KEY_PASSWORD
 *
 * Without them the release APK is unsigned (F-Droid signs its builds itself).
 */
val releaseSigning: Map<String, String>? = run {
    val props = Properties()
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
    fun value(key: String, env: String) = props.getProperty(key) ?: System.getenv(env)
    val values = mapOf(
        "storeFile" to value("storeFile", "NIIMSTUDIO_STORE_FILE"),
        "storePassword" to value("storePassword", "NIIMSTUDIO_STORE_PASSWORD"),
        "keyAlias" to value("keyAlias", "NIIMSTUDIO_KEY_ALIAS"),
        "keyPassword" to value("keyPassword", "NIIMSTUDIO_KEY_PASSWORD"),
    )
    if (values.values.all { !it.isNullOrBlank() }) values.mapValues { it.value!! } else null
}

android {
    namespace = "io.github.tobsef.niimstudio"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.tobsef.niimstudio"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.6.1"
        buildConfigField("String", "LANGUAGES", "\"${appLanguages.joinToString(",")}\"")
    }

    androidResources {
        // Lists the languages for Android 13+ per-app language settings.
        generateLocaleConfig = true
    }

    bundle {
        // The language can be switched in the app, so every translation must ship.
        language { enableSplit = false }
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getValue("storeFile"))
                storePassword = releaseSigning.getValue("storePassword")
                keyAlias = releaseSigning.getValue("keyAlias")
                keyPassword = releaseSigning.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without a key the release APK stays unsigned – that is what F-Droid expects, as it
            // signs the apps it builds itself. For testing on a device use the debug build.
            if (releaseSigning != null) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn("NiimStudio: no release key configured (keystore.properties) – release APK is unsigned.")
            }
            // Reproducible builds: no git metadata in the APK.
            vcsInfo { include = false }
        }
    }

    dependenciesInfo {
        // No Google-encrypted dependency list in the signing block: F-Droid rejects it, and it
        // would differ between builds (reproducible builds).
        includeInApk = false
        includeInBundle = false
    }

    lint {
        checkReleaseBuilds = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    // No jvmToolchain: it needs a JDK download (foojay), which F-Droid does not allow. Gradle runs
    // on any JDK 17+; the bytecode target is fixed here and in compileOptions.
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// APK files are named like "NiimStudio-0.3-release.apk".
base {
    archivesName.set("NiimStudio-${android.defaultConfig.versionName}")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.caverock:androidsvg-aar:1.4")

    testImplementation("junit:junit:4.13.2")
}
