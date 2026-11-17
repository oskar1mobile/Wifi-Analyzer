// Das Android SDK wird nur für das Modul :app benötigt. Ohne SDK (z. B. in
// reinen JVM-Umgebungen) wird nur das plattformunabhängige Modul :core gebaut.
fun androidSdkAvailable(dir: File): Boolean =
    listOf("ANDROID_HOME", "ANDROID_SDK_ROOT").any { !System.getenv(it).isNullOrBlank() } ||
        File(dir, "local.properties").exists()

buildscript {
    val sdk = listOf("ANDROID_HOME", "ANDROID_SDK_ROOT").any { !System.getenv(it).isNullOrBlank() } ||
        File(settingsDir, "local.properties").exists()
    repositories {
        if (sdk) google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.20")
        if (sdk) {
            classpath("com.android.tools.build:gradle:8.9.1")
            classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.1.20")
        }
    }
}

val hasAndroidSdk = androidSdkAvailable(settingsDir)

dependencyResolutionManagement {
    repositories {
        if (hasAndroidSdk) google()
        mavenCentral()
    }
}

rootProject.name = "WifiAnalyzer"
include(":core")
if (hasAndroidSdk) {
    include(":app")
} else {
    logger.lifecycle("Android SDK nicht gefunden – es wird nur das Modul :core gebaut.")
}
