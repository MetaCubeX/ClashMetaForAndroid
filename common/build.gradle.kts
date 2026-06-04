plugins {
    kotlin("android")
    id("com.android.library")
    id("kotlinx-serialization")
}

dependencies {
    compileOnly(project(":hideapi"))

    implementation(libs.kotlin.coroutine)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.androidx.core)

    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
}

android {
    // Stub Android APIs (android.util.Base64 in MaybeBase64, android.util.Log, …)
    // return default values inside JVM unit tests instead of throwing
    // "Method X not mocked".
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}
