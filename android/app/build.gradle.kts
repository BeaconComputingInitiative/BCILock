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

    // Debug builds MUST install alongside the enrolled V1 authenticator.
    // Distinct applicationId means distinct AndroidKeyStore identity and staging trust file.
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".stage2test"
            versionNameSuffix = "-parallel"
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
