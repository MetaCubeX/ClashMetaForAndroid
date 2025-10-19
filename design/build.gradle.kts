plugins {
    kotlin("android")
    id("com.android.library")
    alias(libs.plugins.compose.compiler)
    id("kotlin-parcelize")
    id("dev.oom-wg.purejoy.mlang")
}

android {
    compileSdk = 36
}

MLang {
    name = null
    configDir = "./lang"
    baseLang = "zh_CN"
    base = true
    compose = true
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.runtime)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.animation.core)
    implementation(libs.miuix.android)
    implementation(libs.lucide.icons)
    implementation(libs.accompanist.drawablepainter)
    implementation(libs.pangutext)
    implementation("dev.chrisbanes.haze:haze:1.6.10")
    implementation("com.tencent:mmkv:2.2.4")
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
}
