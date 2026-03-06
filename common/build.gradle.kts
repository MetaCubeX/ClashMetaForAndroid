plugins {
    alias(libs.plugins.android.library)
}

dependencies {
    compileOnly(projects.hideapi)

    implementation(libs.kotlin.coroutine)
    implementation(libs.androidx.core)
}
