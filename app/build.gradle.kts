import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import org.gradle.api.GradleException

plugins {
    kotlin("android")
    id("kotlinx-serialization")
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

    // Companion remote-control (clashctl): agent server, controller client, TLS, QR.
    implementation(libs.nanohttpd)
    implementation(libs.okhttp)
    implementation(libs.bouncycastle.pkix)
    implementation(libs.zxing.core)

    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

android {
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            // BouncyCastle ships these in all three (bcpkix/bcutil/bcprov) multi-release jars.
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "META-INF/BCKEY.SF"
            excludes += "META-INF/BCKEY.DSA"
        }
    }
}

tasks.getByName("clean", type = Delete::class) {
    delete(file("release"))
}

// ── Bundled geo databases (supply-chain hardened) ───────────────────────────
// The geoip/geosite databases are seeded into the APK at build time. To keep the
// build reproducible and tamper-evident we PIN the source to an immutable
// meta-rules-dat commit (jsdelivr @<sha> serves that exact tree; the mutable
// @release / latest tags are deliberately NOT used) and VERIFY the SHA-256 of
// every downloaded file against a committed expected hash. A mismatch fails the
// build — a poisoned mirror can never bake a substituted database into a release.
//
// Bump procedure: pick a newer meta-rules-dat `release` commit, update
// geoFilesPin + the five geoFileHashes, done. Runtime geo stays fresh regardless
// via mihomo's own geox-url auto-update (ServiceStore.seedDefaultGeoMirrors) —
// this pinned copy is only the first-run seed.
val geoFilesDownloadDir = "src/main/assets"
val geoFilesPin = "e4a385ed1f568454631491aabfa59766e1c896d7" // meta-rules-dat @release, 2026-07-10
val geoFileSources = mapOf(
    "geoip.metadb" to "geoip.metadb",
    "geoip.dat" to "geoip.dat",
    "geosite.dat" to "geosite.dat",
    "Country.mmdb" to "country.mmdb",
    "ASN.mmdb" to "GeoLite2-ASN.mmdb",
)
val geoFileHashes = mapOf(
    "geoip.metadb" to "bf2357a1ae88c8bb3251ccb454575b37a73b77db901de7374db379d14dbcaa91",
    "geoip.dat" to "83797719facc092e210f8f8e0e5e0b0bdfe06ac90a3a4a3d6a6ab2d781a917ae",
    "geosite.dat" to "cb77421b5ebe0b786d4bce7cb100c532b28ffc0e7b46d7181cd63139433f4526",
    "Country.mmdb" to "3256b2ba2d8f75778fab6fe4e0e1c77ccffbd8774aab8e577251f3803ad95b49",
    "ASN.mmdb" to "08ee4281c0a53f4ea84adf556a183a9deb72c7721c8f0f10cb2662171c082ae1",
)

fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    val bytes = digest.digest()
    val hex = "0123456789abcdef"
    val sb = StringBuilder(bytes.size * 2)
    for (b in bytes) {
        val v = b.toInt() and 0xff
        sb.append(hex[v ushr 4]).append(hex[v and 0x0f])
    }
    return sb.toString()
}

task("downloadGeoFiles") {
    doLast {
        geoFileSources.forEach { (outputFileName, sourceName) ->
            val expected = geoFileHashes[outputFileName]
                ?: throw GradleException("No pinned SHA-256 for $outputFileName")
            val outputPath = file("$geoFilesDownloadDir/$outputFileName")
            outputPath.parentFile.mkdirs()

            // Reuse an already-verified file so we don't re-download every build.
            if (outputPath.isFile && outputPath.length() > 0L && sha256Of(outputPath) == expected) {
                return@forEach
            }

            // Immutable, pinned sources only. jsdelivr first (fast CDN), GitHub raw at the
            // same commit as fallback — both serve the exact pinned tree, so a tamper on
            // either is caught by the hash check below.
            val urls = listOf(
                "https://cdn.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@$geoFilesPin/$sourceName",
                "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/$geoFilesPin/$sourceName",
            )
            var lastError: Exception? = null
            var downloaded = false
            for (url in urls) {
                try {
                    URL(url).openStream().use { input ->
                        Files.copy(input, outputPath.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    downloaded = true
                    break
                } catch (e: Exception) {
                    lastError = e
                    println("WARN: $outputFileName failed from $url (${e.javaClass.simpleName}: ${e.message})")
                }
            }
            if (!downloaded) {
                throw GradleException(
                    "Could not download $outputFileName from the pinned meta-rules-dat commit. " +
                        "Last error: ${lastError?.message}"
                )
            }

            val actual = sha256Of(outputPath)
            if (actual != expected) {
                outputPath.delete()
                throw GradleException(
                    "SHA-256 mismatch for $outputFileName: expected $expected but got $actual. " +
                        "The pinned geo source was tampered with or the pin is stale — refusing to bundle it."
                )
            }
            println("$outputFileName verified (sha256=$actual) from pin $geoFilesPin")
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
