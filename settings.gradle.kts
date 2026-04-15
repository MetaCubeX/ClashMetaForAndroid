pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://raw.githubusercontent.com/MetaCubeX/maven-backup/main/releases")
    }
}

plugins {
    id("com.gradle.develocity") version "4.4.0"
}

develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
        termsOfUseAgree = "yes"
        val isCI = providers.environmentVariable("CI").isPresent
        publishing.onlyIf { isCI }
    }
}

rootProject.name = "ClashMetaForAndroid"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

include(
    ":app",
    ":core",
    ":service",
    ":design",
    ":common",
    ":hideapi",
)
