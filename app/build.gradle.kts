import com.android.build.gradle.AppExtension
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    kotlin("android")
    kotlin("kapt")
    id("com.android.application")
    alias(libs.plugins.compose.compiler)
}

kapt {
    correctErrorTypes = true
    useBuildCache = true
    arguments {
        arg("kotlin.version", "2.1.0")
    }
}

dependencies {
    compileOnly(project(":hideapi"))
    implementation(project(":core"))
    implementation(project(":service"))
    implementation(project(":design"))
    implementation(project(":common"))
    implementation(libs.kotlin.coroutine)
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.coordinator)
    implementation(libs.androidx.recyclerview)
    implementation(libs.google.material)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.runtime)
    implementation(libs.androidx.foundation)
    implementation(libs.miuix.android)
    implementation("com.tencent:mmkv:2.2.4")
    implementation(libs.coil.compose)

    // 调试工具
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

extensions.configure<AppExtension> {
    productFlavors {
        getByName("alpha") {
            resValue("string", "launch_name", "@string/launch_name_alpha")
            resValue("string", "application_name", "@string/application_name_alpha")
        }
        getByName("meta") {
            resValue("string", "launch_name", "@string/launch_name_meta")
            resValue("string", "application_name", "@string/application_name_meta")
        }
    }
}

tasks.getByName("clean", type = Delete::class) {
    delete(file("release"))
}

// GeoFiles 下载配置
val geoFilesDownloadDir = "src/main/assets"

tasks.register("downloadGeoFiles") {
    description = "Download GeoIP and GeoSite databases from MetaCubeX"
    group = "build setup"

    val geoFilesUrls = mapOf(
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.metadb" to "geoip.metadb",
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat" to "geosite.dat",
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/GeoLite2-ASN.mmdb" to "ASN.mmdb",
    )

    doLast {
        geoFilesUrls.forEach { (downloadUrl, outputFileName) ->
            val url = URI(downloadUrl).toURL()
            val outputPath = file("$geoFilesDownloadDir/$outputFileName")
            outputPath.parentFile.mkdirs()
            url.openStream().use { input: java.io.InputStream ->
                Files.copy(input, outputPath.toPath(), StandardCopyOption.REPLACE_EXISTING)
                println("$outputFileName downloaded to $outputPath")
            }
        }
    }
}

afterEvaluate {
    val downloadGeoFilesTask = tasks.named("downloadGeoFiles")

    tasks.forEach {
        if (it.name.startsWith("assemble")) {
            it.dependsOn(downloadGeoFilesTask)
        }
    }
}

tasks.register<Delete>("cleanGeoFiles") {
    description = "Clean downloaded GeoIP and GeoSite databases"
    group = "build setup"
    delete(file(geoFilesDownloadDir))
}
