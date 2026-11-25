import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * Release signing.
 *
 * The signing keystore for official builds is committed on purpose: this is a
 * sideload-only app published as GitHub Releases, so the important property is
 * that every release is signed with the SAME key and users can update in place.
 * Values can be overridden with a git-ignored `keystore.properties`.
 */
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun prop(key: String, fallback: String): String =
    keystoreProps.getProperty(key) ?: System.getenv(key) ?: fallback

android {
    namespace = "com.pocketdev.lumo_digital_assistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pocketdev.lumo_digital_assistant"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        create("release") {
            val ks = rootProject.file(prop("storeFile", "keystore/lumo-release.jks"))
            if (ks.exists()) {
                storeFile = ks
                storePassword = prop("storePassword", "lumo-bridge-release")
                keyAlias = prop("keyAlias", "lumo")
                keyPassword = prop("keyPassword", "lumo-bridge-release")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
        debug {
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.10" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.8.0")
    implementation("androidx.activity:activity-compose:1.5.1")
    implementation("androidx.compose.ui:ui:1.3.0")
    implementation("androidx.compose.foundation:foundation:1.3.0")
    implementation("androidx.compose.material3:material3:1.0.0")
}
