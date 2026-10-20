plugins {
    kotlin("android")
    kotlin("kapt")
    id("com.android.library")
    alias(libs.plugins.compose.compiler)
}

android {
    buildFeatures.compose = true
}

dependencies {
    implementation(project(":common"))
    implementation(project(":core"))
    implementation(project(":service"))

    implementation(libs.kotlin.coroutine)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.coordinator)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.viewpager)
    implementation(libs.google.material)
    implementation(libs.zxing.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.icons)
    implementation(libs.androidx.compose.material)

    testImplementation(libs.junit)
}
