plugins {
    id("com.android.library")
    kotlin("android")
}

dependencies {
    compileOnly(project(":hideapi"))
    implementation(libs.kotlin.coroutine)
    implementation(libs.androidx.core)
}
