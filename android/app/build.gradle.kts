plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.beaconcomputing.bcilock"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.beaconcomputing.bcilock"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "1.6.3-stage2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
