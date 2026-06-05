plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.d06.sdk.input"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
}
