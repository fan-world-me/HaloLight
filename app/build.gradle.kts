plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import java.util.Properties

// Підпис: keystore.properties (локально), fan_world_me.jks або змінні оточення (CI)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

val ksPath: String? = keystoreProperties.getProperty("storeFile")
    ?: System.getenv("KEYSTORE_PATH")
    ?: if (rootProject.file("fan_world_me.jks").exists()) rootProject.file("fan_world_me.jks").absolutePath else null

val ksPassword: String? = keystoreProperties.getProperty("storePassword")
    ?: System.getenv("KEYSTORE_PASSWORD")

val kAlias: String = keystoreProperties.getProperty("keyAlias")
    ?: System.getenv("KEY_ALIAS")
    ?: "fan_world_me"

val kPassword: String? = keystoreProperties.getProperty("keyPassword")
    ?: System.getenv("KEY_PASSWORD")
    ?: ksPassword

val hasReleaseSigning = !ksPath.isNullOrBlank() && !ksPassword.isNullOrBlank()

android {
    namespace = "dev.fanworld.halolite"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.fanworld.halolite"
        minSdk = 26
        targetSdk = 34
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 6
        versionName = System.getenv("VERSION_NAME") ?: "1.0.6"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                val resolvedFile = rootProject.file(ksPath!!)
                storeFile = if (resolvedFile.exists()) resolvedFile else file(ksPath)
                storePassword = ksPassword
                keyAlias = kAlias
                keyPassword = kPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // якщо заданий пароль та ключ, підписуємо релізним ключем; інакше debug для безпеки
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
