import com.android.build.api.variant.FilterConfiguration
import de.undercouch.gradle.tasks.download.Download
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.legacyKapt)
    alias(libs.plugins.download)
}

android {
    namespace = "com.github.kr328.clash"
    defaultConfig {
        applicationId = "com.github.metacubex.clash"
        targetSdk = 35
        versionCode = 211023
        versionName = "2.11.23"
        resValue("integer", "release_code", versionCode.toString())
        resValue("string", "release_name", "v$versionName")
        ndk.abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
    }

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(localProperties::load)
    }
    val removeSuffix = localProperties.getProperty("remove.suffix")?.toBoolean() == true

    productFlavors {
        named("alpha") {
            resValue("string", "launch_name", "@string/launch_name_alpha")
            resValue("string", "application_name", "@string/application_name_alpha")
            if (!removeSuffix) {
                applicationIdSuffix = ".alpha"
                versionNameSuffix = ".Alpha"
            }
        }
        named("meta") {
            isDefault = true
            resValue("string", "launch_name", "@string/launch_name_meta")
            resValue("string", "application_name", "@string/application_name_meta")
            if (!removeSuffix) {
                applicationIdSuffix = ".meta"
                versionNameSuffix = ".Meta"
            }
        }
    }

    val keystore = rootProject.file("signing.properties")
    val releaseSigning = if (keystore.exists()) {
        signingConfigs.create("release") {
            val prop = Properties()
            keystore.inputStream().use(prop::load)
            storeFile = rootProject.file("release.keystore")
            storePassword = prop.getProperty("keystore.password")
            keyAlias = prop.getProperty("key.alias")
            keyPassword = prop.getProperty("key.password")
        }
    } else {
        signingConfigs["debug"]
    }

    buildTypes {
        all {
            signingConfig = releaseSigning
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            versionNameSuffix = ".debug"
        }
    }

    buildFeatures {
        dataBinding = true
        resValues = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes.add("DebugProbesKt.bin")
        }
    }

    splits {
        abi {
            isEnable = true
            isUniversalApk = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            // TODO: https://github.com/android/gradle-recipes/blob/cbe7c7dea2a3f5b1764756f24bf453d1235c80e2/listenToArtifacts/README.md
            with(output as com.android.build.api.variant.impl.VariantOutputImpl) {
                val abiName = output.filters
                    .find { it.filterType == FilterConfiguration.FilterType.ABI }
                    ?.identifier ?: "universal"
                val newApkName = "cmfa-${versionName.get()}-meta-$abiName-${variant.buildType}.apk"
                outputFileName = newApkName
            }
        }
    }
}

dependencies {
    compileOnly(projects.hideapi)

    implementation(projects.core)
    implementation(projects.service)
    implementation(projects.design)
    implementation(projects.common)

    implementation(libs.kotlin.coroutine)
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.coordinator)
    implementation(libs.androidx.recyclerview)
    implementation(libs.google.material)
    implementation(libs.quickie.bundled)
}

val downloadGeoFiles by tasks.registering(Download::class) {
    src(
        listOf(
            "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.metadb",
            "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat",
            "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/GeoLite2-ASN.mmdb",
        ),
    )
    dest("src/main/assets")
    onlyIfModified(true)
    eachFile {
        if (name == "GeoLite2-ASN.mmdb") {
            name = "ASN.mmdb"
        }
    }
}

tasks.preBuild {
    dependsOn(downloadGeoFiles)
}

tasks.clean {
    delete(downloadGeoFiles)
}
