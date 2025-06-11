plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.smartwatchhapticsystem"
    compileSdk = 34 // ✅ Compiling for Android 14 (API 34)

    defaultConfig {
        applicationId = "com.example.smartwatchhapticsystem"
        minSdk = 30 // ✅ Minimum SDK 26 (Android 8.0)
        targetSdk = 34 // ✅ Targeting Android 14 (API 34)
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17 // ✅ Java 17 Support
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // ✅ AndroidX Core & Material Components
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation("androidx.core:core:1.12.0")


    // ✅ Networking
    implementation(libs.volley)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)

    // ✅ Google Play Services Location API
    implementation(libs.play.services.location)

    // ✅ Testing Dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

