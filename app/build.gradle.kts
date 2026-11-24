plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "tv.myliststv.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "tv.myliststv.launcher"
        minSdk = 24
        targetSdk = 34
        // CI passes -PbuildNumber=<run number>, so every published build is newer than the last and matches its release tag.
        val build = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "0.${1 + build / 100}.${build % 100}"   // 0.1.99, then 0.2.0 (see data/Version.kt)
        // 32-bit first: many cheap boxes run a 32-bit userland on a 64-bit chip.
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }

    // One fixed key for sideloaded builds, so every build installs over the last one.
    // It is the standard public "android" debug key, not a secret. Use a private key if this ever goes to a store.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore"); storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.core:core-ktx:1.13.1")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
}

dependencies {
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
}

dependencies {
    implementation("androidx.tvprovider:tvprovider:1.0.0")
}
