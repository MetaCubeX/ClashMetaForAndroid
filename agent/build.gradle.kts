plugins {
    kotlin("android")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(libs.kotlin.coroutine)
    implementation(libs.kotlin.serialization.json)

    testImplementation(kotlin("test"))
}
