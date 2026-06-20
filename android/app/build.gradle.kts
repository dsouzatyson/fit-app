plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    kotlin("kapt")
}

android {
    namespace = "com.fitapp.imageeditor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fitapp.imageeditor"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Emulator: 10.0.2.2 maps to host localhost. Physical device: use your LAN IP.
        buildConfigField("String", "BACKEND_URL", "\"http://192.168.0.91:3000\"")
        // ── Mock flag ──────────────────────────────────────────────────────────
        // Set to true to skip fal.ai calls during UI testing (saves money).
        // The person photo is used as the fake result so the full UI flow works.
        // Flip to false before any real demo or production build.
        buildConfigField("Boolean", "MOCK_GENERATION", "false")
        // ── Gallery picker ─────────────────────────────────────────────────────
        // Set to false to hide the Gallery tab on the garment screen.
        // Code is preserved — flip to true to re-enable without any changes.
        buildConfigField("Boolean", "ENABLE_GALLERY_PICKER", "false")
        // ── Affiliate tag ──────────────────────────────────────────────────────
        // Amazon Associates tag appended to redirect URLs.
        // Change this value here — no Kotlin code edits required.
        // Leave empty ("") to disable affiliate tagging entirely.
        buildConfigField("String", "AFFILIATE_TAG", "\"viralcartf031-21\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Retrofit + OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
