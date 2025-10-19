plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
}


androidComponents {
    onVariants { variant ->
        val variantName = variant.name

        kotlin.sourceSets {
            getByName(variantName) {
                kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/$variantName/kotlin"))
            }
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":common"))
    ksp(libs.kaidl.compiler)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlin.coroutine)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.androidx.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kaidl.runtime)
    implementation(libs.rikkax.multiprocess)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
}
