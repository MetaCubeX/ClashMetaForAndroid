@file:Suppress("UNUSED_VARIABLE")

import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import java.util.*

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        maven("https://raw.githubusercontent.com/MetaCubeX/maven-backup/main/releases")
        maven("https://jitpack.io")
    }
    dependencies {
        classpath(libs.build.android)
        classpath(libs.build.kotlin.common)
        classpath(libs.build.kotlin.serialization)
        classpath(libs.build.ksp)
        classpath(libs.build.golang)
        classpath("dev.oom-wg.PureJoy-MultiLang:plugin:-SNAPSHOT")
    }
}

subprojects {

    val isApp = name == "app"

    apply(plugin = if (isApp) "com.android.application" else "com.android.library")

    extensions.configure<BaseExtension> {
        buildFeatures.buildConfig = true
        defaultConfig {
            if (isApp) {
                applicationId = "plus.yumeyuka.yumebox"
            }

            // 命名空间配置
            project.name.let { name ->
                namespace = if (name == "app") "com.github.kr328.clash"
                else "com.github.kr328.clash.$name"
            }

            minSdk = 26
            targetSdk = 36

            versionName = "2.11.17"
            versionCode = 211017

            resValue("string", "release_name", "v$versionName")
            resValue("integer", "release_code", "$versionCode")

            @Suppress("UnstableApiUsage")
            externalNativeBuild {
                cmake {
                    abiFilters("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
                }
            }

            if (!isApp) {
                consumerProguardFiles("consumer-rules.pro")
            } else {
                setProperty("archivesBaseName", "YumeBox")
            }
        }

        ndkVersion = "27.2.12479018"

        compileSdkVersion(defaultConfig.targetSdk!!)

        if (isApp) {
            packagingOptions {
                resources {
                    excludes.add("DebugProbesKt.bin")
                }
                jniLibs {
                    useLegacyPackaging = true
                }
            }
        }

        flavorDimensions("feature")
        
        productFlavors {
            create("alpha")
            create("meta")
        }

        productFlavors.named("alpha") {
            dimension = "feature"
            versionNameSuffix = ".Alpha"

            buildConfigField("boolean", "PREMIUM", "Boolean.parseBoolean(\"false\")")

            resValue("string", "launch_name", "@string/launch_name_alpha")
            resValue("string", "application_name", "@string/application_name_alpha")
        }

        productFlavors.named("meta") {
            dimension = "feature"
            versionNameSuffix = ".Meta"

            buildConfigField("boolean", "PREMIUM", "Boolean.parseBoolean(\"false\")")
        }

        if (isApp) {
            (this as com.android.build.gradle.internal.dsl.BaseAppModuleExtension).apply {
                productFlavors.named("alpha") {
                    this.apply {
                        isDefault = true
                        applicationIdSuffix = ".alpha"
                    }
                }

                productFlavors.named("meta") {
                    this.apply {
                        applicationIdSuffix = ".meta"
                    }
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
        }

        signingConfigs {
            val keystore = rootProject.file("signing.properties")
            if (keystore.exists()) {
                create("release").apply {
                    val prop = Properties().apply {
                        keystore.inputStream().use { load(it) }
                    }

                    storeFile = rootProject.file("release.keystore")
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
                signingConfig = signingConfigs.findByName("release") ?: signingConfigs["debug"]
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            }
            named("debug") {
                versionNameSuffix = ".debug"
            }
        }

        buildFeatures.apply {
            viewBinding = false
        }

        if (isApp) {
            this as AppExtension

            splits {
                abi {
                    isEnable = true
                    isUniversalApk = false
                    reset()
                    val abiFilter = project.findProperty("abiFilter") as String?
                    if (abiFilter != null) {
                        include(abiFilter)
                    } else {
                        include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
                    }
                }
            }
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_23
            targetCompatibility = JavaVersion.VERSION_23
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_23)
                freeCompilerArgs.add("-Xskip-metadata-version-check")
                freeCompilerArgs.add("-Xsuppress-version-warnings")
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL

    doLast {
        val sha256 = java.net.URI("$distributionUrl.sha256").toURL().openStream()
            .use { it.reader().readText().trim() }

        file("gradle/wrapper/gradle-wrapper.properties")
            .appendText("distributionSha256Sum=$sha256")
    }
}
