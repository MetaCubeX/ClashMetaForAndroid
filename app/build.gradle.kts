import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.GradleException

plugins {
    kotlin("android")
    id("com.android.application")
}

dependencies {
    compileOnly(project(":hideapi"))

    implementation(project(":core"))
    implementation(project(":service"))
    implementation(project(":design"))
    implementation(project(":common"))

    implementation(libs.kotlin.coroutine)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.androidx.core)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.coordinator)
    implementation(libs.androidx.recyclerview)
    implementation(libs.google.material)
    implementation(libs.quickie.bundled)
    implementation(libs.androidx.activity.ktx)
}

tasks.getByName("clean", type = Delete::class) {
    delete(file("release"))
}

val geoFilesDownloadDir = "src/main/assets"

task("downloadGeoFiles") {

    // GitHub raw TLS can fail in some networks; try jsDelivr (@release) first, then GitHub releases.
    val geoFileMirrors = mapOf(
        "geoip.metadb" to listOf(
            "https://cdn.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/geoip.metadb",
            "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.metadb",
        ),
        "geosite.dat" to listOf(
            "https://cdn.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/geosite.dat",
            "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat",
        ),
        "ASN.mmdb" to listOf(
            "https://cdn.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/GeoLite2-ASN.mmdb",
            "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/GeoLite2-ASN.mmdb",
        ),
    )

    doLast {
        geoFileMirrors.forEach { (outputFileName, urls) ->
            val outputPath = file("$geoFilesDownloadDir/$outputFileName")
            outputPath.parentFile.mkdirs()

            if (outputPath.isFile && outputPath.length() > 0L) {
                var ok = false
                for (url in urls) {
                    try {
                        URL(url).openStream().use { input ->
                            Files.copy(input, outputPath.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        }
                        println("$outputFileName downloaded from $url -> $outputPath")
                        ok = true
                        break
                    } catch (e: Exception) {
                        println("WARN: $outputFileName failed from $url (${e.javaClass.simpleName}: ${e.message})")
                    }
                }
                if (!ok) {
                    println("WARN: keeping existing $outputFileName (${outputPath.length()} bytes) after failed refresh")
                }
                return@forEach
            }

            var last: Exception? = null
            for (url in urls) {
                try {
                    URL(url).openStream().use { input ->
                        Files.copy(input, outputPath.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    println("$outputFileName downloaded from $url -> $outputPath")
                    last = null
                    break
                } catch (e: Exception) {
                    last = e
                    println("WARN: $outputFileName failed from $url (${e.javaClass.simpleName}: ${e.message})")
                }
            }
            if (last != null && (!outputPath.isFile || outputPath.length() == 0L)) {
                throw GradleException(
                    "Could not download $outputFileName (network/TLS). Last error: ${last?.message}. " +
                        "Place files manually under $geoFilesDownloadDir or retry with a stable connection."
                )
            }
        }
    }
}

afterEvaluate {
    val downloadGeoFilesTask = tasks["downloadGeoFiles"]

    tasks.forEach {
        if (it.name.startsWith("assemble")) {
            it.dependsOn(downloadGeoFilesTask)
        }
    }
}

tasks.getByName("clean", type = Delete::class) {
    delete(file(geoFilesDownloadDir))
}