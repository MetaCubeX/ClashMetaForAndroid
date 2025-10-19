pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
        maven("https://raw.githubusercontent.com/MetaCubeX/maven-backup/main/releases")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://raw.githubusercontent.com/MetaCubeX/maven-backup/main/releases") }
        maven { url = uri("https://maven.kr328.app/releases") }
    }
}

rootProject.name = "YumeBox"

include(":app")
include(":core")
include(":service")
include(":design")
include(":common")
include(":hideapi")
