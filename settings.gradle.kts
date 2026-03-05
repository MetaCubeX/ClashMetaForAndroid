rootProject.name = "ClashMetaForAndroid"

include(":app")
include(":core")
include(":service")
include(":design")
include(":common")
include(":hideapi")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    }
}

plugins { id("com.gradle.develocity") version "4.3.2" }

develocity {
  buildScan {
    termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
    termsOfUseAgree = "yes"
    val isCI = providers.environmentVariable("CI").isPresent
    publishing.onlyIf { isCI }
  }
}
