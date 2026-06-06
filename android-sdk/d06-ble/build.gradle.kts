plugins {
    id("com.android.library")
    kotlin("android")
    id("maven-publish")
}

android {
    namespace = "com.d06.sdk.ble"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
}

publishing {
    publications {
        create<MavenPublication>("release") {
            artifactId = "d06-ble"
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
