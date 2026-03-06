plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

dependencies {
    compileOnly(projects.hideapi)

    implementation(libs.kotlin.coroutine)
    implementation(libs.androidx.core)
}
