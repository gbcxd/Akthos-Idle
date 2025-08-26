import org.gradle.kotlin.dsl.implementation


plugins {
    id("com.android.application")
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.obliviongatestudio.akthosidle"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.obliviongatestudio.akthosidle"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables.useSupportLibrary = true

        // (optional) Instrumented test runner – keeps AndroidTest happy if you add any later
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    testOptions {
        // Let local unit tests run code that references Android SDK types by returning defaults
        unitTests.isReturnDefaultValues = true
        // (optional) include resources in unit tests if you ever need them
        // unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // --- Your existing runtime deps ---
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.4")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(libs.firebase.crashlytics.buildtools)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.firebase.firestore)
    implementation(libs.preference)
    implementation(libs.firebase.auth)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.google.firebase:firebase-bom:33.2.0")


    // --- Unit test (local JVM) ---
    testImplementation("junit:junit:4.13.2")
    // Helpful to use Android classes in local unit tests; safe to keep even if unused
    testImplementation("androidx.test:core:1.5.0")

    // --- Instrumented test (on device/emulator) — optional but standard ---
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
