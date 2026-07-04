plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "id.beesmillah.printbridge"
    compileSdk = 34

    defaultConfig {
        applicationId = "id.beesmillah.printbridge"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    // SDK Epson ePOS2 (paket "ePOS SDK for Android" v2.37): ePOS2.jar di
    // app/libs/ + libepos2.so per-ABI di app/src/main/jniLibs/ — keduanya
    // disalin dari paket unduhan Epson (lihat README.md).
    implementation(files("libs/ePOS2.jar"))

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
