plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("kapt") // ✅ ADDED - Room sathi must aahe
}

kapt {
    correctErrorTypes = true
}

android {
    namespace = "com.vedx.vedxsuper"
    compileSdk = 35
    
    defaultConfig {
        applicationId = "com.vedx.vedxsuper"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "3.0-AI-PRO"
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler) // ✅ Room annotation processor
    implementation(libs.coroutines)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // Security & Biometric
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")
    
    // DataStore (optional, for settings)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Unit Testing
    testImplementation("junit:junit:4.13.2")
}
