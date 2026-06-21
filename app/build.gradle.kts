import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Upload-key signing for the Play AAB. Reads gitignored keystore.properties if present;
// it is absent in CI, so the GitHub APK path (apk-action) is unaffected.
// keystore.properties format (paths relative to repo root):
//   storeFile=upload-keystore.jks
//   storePassword=...
//   keyAlias=upload
//   keyPassword=...
val keystorePropsFile = rootProject.file("keystore.properties")
val hasKeystore = keystorePropsFile.exists()
val keystoreProps = Properties().apply {
    if (hasKeystore) FileInputStream(keystorePropsFile).use { load(it) }
}

android {
    namespace = "com.t9dialer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.t9dialer"
        minSdk = 23
        targetSdk = 35
        versionCode = 106
        versionName = "1.6.0"
    }

    signingConfigs {
        if (hasKeystore) {
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
            isMinifyEnabled = false
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
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

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
