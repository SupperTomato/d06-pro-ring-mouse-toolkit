pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "d06-android-sdk"

include(":d06-core")
include(":d06-input")
include(":d06-ble")
include(":d06-remapper")
include(":d06-sample")
