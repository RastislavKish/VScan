plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.jetbrainsKotlinSerialization)
}

android {
    namespace = "com.rastislavkish.vscan"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rastislavkish.vscan"
        minSdk = 24
        targetSdk = 34
        versionCode = 13
        versionName = "0.1.3"

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

implementation(libs.rtk.kotlin.android)

implementation(libs.camera.core)
implementation(libs.camera.lifecycle)
implementation(libs.camera.camera2)
implementation(libs.camera.view)
implementation(libs.ktor.client.core)
implementation(libs.ktor.client.cio)
implementation(libs.kotlinx.coroutines.core)
implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
