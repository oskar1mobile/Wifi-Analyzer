import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release-Signing: Keystore und Passwort kommen ausschließlich aus Umgebungsvariablen,
// damit nie ein Geheimnis im Repository landet.
//   KS_FILE  – Pfad zur Keystore-Datei (Standard: android/release.keystore)
//   KS_PASS  – Keystore-Passwort (bei PKCS12 zugleich das Schlüssel-Passwort)
//   KS_ALIAS – Schlüssel-Alias (Standard: wlan-analyzer)
// Mit -PrequireReleaseSigning=true bricht der Build ab, statt auf den Debug-Schlüssel auszuweichen.
val releaseKeystore: File = System.getenv("KS_FILE")?.takeIf { it.isNotBlank() }?.let { file(it) }
    ?: rootProject.file("release.keystore")
val releaseKsPass: String? = System.getenv("KS_PASS")?.takeIf { it.isNotEmpty() }
val hasReleaseSigning = releaseKsPass != null && releaseKeystore.isFile
val requireReleaseSigning = providers.gradleProperty("requireReleaseSigning").orNull == "true"

if (!hasReleaseSigning) {
    val reason = when {
        releaseKsPass == null -> "KS_PASS ist nicht gesetzt"
        else -> "Keystore nicht gefunden: $releaseKeystore"
    }
    if (requireReleaseSigning) throw GradleException("Release-Signing nicht möglich – $reason.")
    logger.warn("WARNUNG: $reason – die Release-APK wird mit dem Debug-Schlüssel signiert.")
}

android {
    namespace = "app.wifianalyzer"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.wifianalyzer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseKsPass
                keyAlias = System.getenv("KS_ALIAS")?.takeIf { it.isNotBlank() } ?: "wlan-analyzer"
                keyPassword = releaseKsPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2025.04.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
