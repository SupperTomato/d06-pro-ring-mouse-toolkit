plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.d06.sdk.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.d06.sdk.sample"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":d06-core"))
    implementation(project(":d06-input"))
    implementation(project(":d06-ble"))
    implementation(project(":d06-remapper"))
}
