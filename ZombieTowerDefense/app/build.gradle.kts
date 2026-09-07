plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Each CI run stamps its number here so every published build carries a higher
// versionCode than the last and installs as a genuine update.
val buildNumber = (System.getenv("BUILD_NUMBER") ?: "1").toInt()

android {
    namespace = "com.enache.zombietd"
    compileSdk = 35

    // A fixed signing key shared by every build. Android only allows an app to be
    // replaced in place when the new APK carries the same signature, so this must
    // stay stable across releases. Debug-quality key, deliberately not secret.
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore/zombie.jks")
            storePassword = "zombietd"
            keyAlias = "zombie"
            keyPassword = "zombietd"
        }
    }

    defaultConfig {
        applicationId = "com.enache.zombietd"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.$buildNumber"
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
