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
}
