plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.d06.sdk.remapper"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
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
    api(project(":d06-core"))
}
