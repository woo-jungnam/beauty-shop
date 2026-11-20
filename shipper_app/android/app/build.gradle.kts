plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

val signingValues = rootProject.file("../../.env").takeIf { it.isFile }?.useLines { lines ->
    lines.mapNotNull { line ->
        val key = line.substringBefore('=').trim()
        if (key.startsWith("SHIPPER_ANDROID_") && '=' in line)
            key to line.substringAfter('=').trim().trim('"', 39.toChar())
        else null
    }.toMap()
}.orEmpty()

fun signingValue(key: String): String? =
    (providers.environmentVariable(key).orNull ?: signingValues[key])?.takeIf { it.isNotBlank() }

val releaseStore = signingValue("SHIPPER_ANDROID_KEYSTORE")
val releaseStorePassword = signingValue("SHIPPER_ANDROID_STORE_PASSWORD")
val releaseAlias = signingValue("SHIPPER_ANDROID_KEY_ALIAS")
val releaseKeyPassword = signingValue("SHIPPER_ANDROID_KEY_PASSWORD")
val releaseSigningReady = listOf(releaseStore, releaseStorePassword, releaseAlias, releaseKeyPassword)
    .all { it != null }
val releaseStoreFile = releaseStore?.let { rootProject.file("../..").resolve(it) }

android {
    namespace = "com.beautyshop.beautyshop_shipper"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.beautyshop.beautyshop_shipper"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        manifestPlaceholders["shipperCleartext"] = "false"
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        if (releaseSigningReady && releaseStoreFile?.isFile == true) {
            create("shipperRelease") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["shipperCleartext"] = "true"
        }
        release {
            signingConfig = signingConfigs.findByName("shipperRelease")
        }
    }
}

val verifyShipperReleaseSigning by tasks.registering {
    doLast {
        check(releaseSigningReady && releaseStoreFile?.isFile == true) {
            "Configure SHIPPER_ANDROID_KEYSTORE, SHIPPER_ANDROID_STORE_PASSWORD, SHIPPER_ANDROID_KEY_ALIAS and SHIPPER_ANDROID_KEY_PASSWORD before building a release."
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyShipperReleaseSigning)
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
