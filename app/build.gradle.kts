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
