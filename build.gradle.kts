@file:Suppress("UNUSED_VARIABLE")

import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import java.net.URL
import java.io.ByteArrayOutputStream
import java.util.*
import java.security.MessageDigest

buildscript {
    repositories {
        mavenCentral()
        google()
        maven("https://raw.githubusercontent.com/MetaCubeX/maven-backup/main/releases")
    }
    dependencies {
        classpath(libs.build.android)
        classpath(libs.build.kotlin.common)
        classpath(libs.build.kotlin.serialization)
        classpath(libs.build.ksp)
        classpath(libs.build.golang)
    }
}

subprojects {
    repositories {
        mavenCentral()
        google()
        maven("https://raw.githubusercontent.com/MetaCubeX/maven-backup/main/releases")
    }

    val isApp = name == "app"
    val targetAbis = if (providers.gradleProperty("agentArm64Only").orNull.toBoolean()) {
        listOf("arm64-v8a")
    } else {
        listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
    }

    // Monotonic versionCode derived from git history. Counts every commit reachable
    // from HEAD, so it only grows over time and differs per build/CI checkout.
    // floor(versionCode / 1000) groups builds into "major.minor" blocks; the
    // remainder is the per-release sequence, mirroring the upstream 2.11.32 -> 211032
    // convention (versionCode = major * 10000 + minor * 100 + sequence).
    val buildVersionCode: Int by lazy {
        val base = 211032
        val commits = try {
            val stdout = ByteArrayOutputStream()
            exec {
                commandLine("git", "rev-list", "--count", "HEAD")
                standardOutput = stdout
            }
            stdout.toString().trim().toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
        base + commits
    }

    // The development signing key must stay stable across machines so every
    // build can update previous installs without data loss. If it is missing,
    // fail fast with a hint instead of silently creating a fresh key.
    val agentDebugKeystore = file(System.getProperty("user.home") + "/.android/debug.keystore")
    if (isApp && !agentDebugKeystore.exists()) {
        throw GradleException(
            "Missing development keystore ${agentDebugKeystore}. " +
            "Copy the repository's backed-up key to this path (see docs/SIGNING.md) " +
            "or restore it from CI secret AGENT_DEBUG_KEYSTORE, otherwise updates " +
            "would lose installed-app data."
        )
    }

    apply(plugin = if (isApp) "com.android.application" else "com.android.library")

    fun queryConfigProperty(key: String): Any? {
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        } else {
            return null
        }
        return localProperties.getProperty(key)
    }

    extensions.configure<BaseExtension> {
        buildFeatures.buildConfig = true
        defaultConfig {
            if (isApp) {
                val customApplicationId = queryConfigProperty("custom.application.id") as? String?
                applicationId = customApplicationId.takeIf { it?.isNotBlank() == true } ?: "com.github.metacubex.clash"
            }

            project.name.let { name ->
                namespace = if (name == "app") "com.github.kr328.clash"
                else "com.github.kr328.clash.$name"
            }

            minSdk = 21
            targetSdk = 35

            versionName = "2.11.32"
            versionCode = buildVersionCode

            resValue("string", "release_name", "v$versionName")
            resValue("integer", "release_code", "$versionCode")

            ndk {
                abiFilters += targetAbis
            }

            externalNativeBuild {
                cmake {
                    abiFilters(*targetAbis.toTypedArray())
                }
            }

            if (!isApp) {
                consumerProguardFiles("consumer-rules.pro")
            } else {
                setProperty("archivesBaseName", "cmfa-$versionName")
            }
        }

        ndkVersion = "29.0.14206865"

        compileSdkVersion(defaultConfig.targetSdk!!)

        if (isApp) {
            packagingOptions {
                resources {
                    excludes.add("DebugProbesKt.bin")
                }
            }
        }

        productFlavors {
            flavorDimensions("feature")

            val removeSuffix = (queryConfigProperty("remove.suffix") as? String)?.toBoolean() == true

            create("alpha") {
                isDefault = true
                dimension = flavorDimensionList[0]
                if (!removeSuffix) {
                    versionNameSuffix = ".Alpha"
                }


                buildConfigField("boolean", "PREMIUM", "Boolean.parseBoolean(\"false\")")

                resValue("string", "launch_name", "@string/launch_name_alpha")
                resValue("string", "application_name", "@string/application_name_alpha")

                if (isApp && !removeSuffix) {
                    applicationIdSuffix = ".alpha"
                }
            }

            create("meta") {

                dimension = flavorDimensionList[0]
                if (!removeSuffix) {
                    versionNameSuffix = ".Meta"
                }

                buildConfigField("boolean", "PREMIUM", "Boolean.parseBoolean(\"false\")")

                resValue("string", "launch_name", "@string/launch_name_meta")
                resValue("string", "application_name", "@string/application_name_meta")

                if (isApp && !removeSuffix) {
                    applicationIdSuffix = ".meta"
                }
            }

            create("agent") {
                dimension = flavorDimensionList[0]
                // Without this an agent release reports the same versionName as an
                // upstream release, so a bug report cannot say which build it came
                // from. The other flavors already carry .Alpha / .Meta.
                if (!removeSuffix) {
                    versionNameSuffix = ".AI"
                }

                buildConfigField("boolean", "PREMIUM", "Boolean.parseBoolean(\"false\")")

                resValue("string", "launch_name", "@string/launch_name_agent")
                resValue("string", "application_name", "@string/application_name_agent")

                if (isApp) {
                    applicationId = "io.github.viewer12.cmfa.agent"
                }
            }
        }

        sourceSets {
            getByName("meta") {
                java.srcDirs("src/foss/java")
            }
            getByName("alpha") {
                java.srcDirs("src/foss/java")
            }
            getByName("agent") {
                java.srcDirs("src/foss/java")
            }
        }

        signingConfigs {
            create("agentDebug") {
                // CI restores this development-only key from the repository's private
                // Actions cache. Use an explicit path so every module signs with it.
                storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }

            if (isApp) {
                // Debug/release signing identity must be stable for updates to install
                // without wiping app data. The keystore backup workflow is documented in
                // docs/SIGNING.md; CI restores the same key from AGENT_DEBUG_KEYSTORE.
                val debugKeystore = file(System.getProperty("user.home") + "/.android/debug.keystore")
                if (debugKeystore.exists()) {
                    val expectedSha256 = providers.gradleProperty("expectedDebugKeySha256").orNull
                    if (expectedSha256 != null) {
                        val actual = debugKeystore.inputStream().use { input ->
                            MessageDigest.getInstance("SHA-256")
                                .digest(input.readBytes())
                                .joinToString("") { "%02x".format(it) }
                        }
                        if (!actual.equals(expectedSha256, ignoreCase = true)) {
                            throw GradleException(
                                "Debug keystore SHA-256 mismatch: expected $expectedSha256, got $actual. " +
                                "Restore the stable key (see docs/SIGNING.md) before building."
                            )
                        }
                    }
                }
            }

            val keystore = rootProject.file("signing.properties")
            if (keystore.exists()) {
                create("release") {
                    val prop = Properties().apply {
                        keystore.inputStream().use(this::load)
                    }

                    // README documents keystore.path, but it was ignored and the
                    // key committed to this repository was used unconditionally.
                    // That file is public, so signing a redistributed build with
                    // it would let anyone forge updates. Honour the property so
                    // the production key can live outside the working tree.
                    val configuredKeystore = prop.getProperty("keystore.path")?.trim()
                    storeFile = if (configuredKeystore.isNullOrEmpty()) {
                        logger.warn(
                            "signing.properties has no keystore.path; falling back to the " +
                            "release.keystore committed in this repository. That key is public — " +
                            "generate your own before distributing builds. See docs/SIGNING.md."
                        )
                        rootProject.file("release.keystore")
                    } else {
                        file(configuredKeystore)
                    }
                    storePassword = prop.getProperty("keystore.password")!!
                    keyAlias = prop.getProperty("key.alias")!!
                    keyPassword = prop.getProperty("key.password")!!
                }
            }
        }

        buildTypes {
            named("release") {
                isMinifyEnabled = isApp
                isShrinkResources = isApp
                // Never silently fall back to the debug key for a release build:
                // that would change the signing identity and break updates.
                val requestedTasks = gradle.startParameter.taskNames.joinToString(" ")
                val buildingRelease = requestedTasks.split(" ").any { it.contains("Release", ignoreCase = true) || it.contains("release", ignoreCase = true) }
                if (buildingRelease) {
                    val releaseConfig = signingConfigs.findByName("release")
                    if (releaseConfig == null) {
                        throw GradleException(
                            "Release builds require signing.properties with the production keystore. " +
                            "See docs/SIGNING.md. Refusing to sign release with the debug key."
                        )
                    }
                    signingConfig = releaseConfig
                } else {
                    signingConfig = signingConfigs.findByName("release") ?: signingConfigs["debug"]
                }
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            }
            named("debug") {
                versionNameSuffix = ".debug"
                signingConfig = signingConfigs["agentDebug"]
            }
        }

        buildFeatures.apply {
            dataBinding {
                isEnabled = name != "hideapi"
            }
        }

        if (isApp) {
            this as AppExtension

            splits {
                abi {
                    isEnable = true
                    isUniversalApk = true
                    reset()
                    include(*targetAbis.toTypedArray())
                }
            }
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
    }
}

task("clean", type = Delete::class) {
    delete(rootProject.buildDir)
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL

    doLast {
        val sha256 = URL("$distributionUrl.sha256").openStream()
            .use { it.reader().readText().trim() }

        file("gradle/wrapper/gradle-wrapper.properties")
            .appendText("distributionSha256Sum=$sha256")
    }
}
