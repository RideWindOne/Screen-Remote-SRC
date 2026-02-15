pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "scrcpy-mobile"
include(":app")
includeBuild("../external/dadb") {
    dependencySubstitution {
        substitute(module("dev.mobile:dadb")).using(project(":dadb"))
        substitute(module("dev.mobile:dadb-android")).using(project(":dadb-android"))
    }
}
