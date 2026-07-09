plugins {
    kotlin("android")
    id("kotlinx-serialization")
    id("com.android.library")
    id("com.google.devtools.ksp")
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
    implementation("org.yaml:snakeyaml:2.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
    // Real org.json for JVM unit tests (android.jar's is stubbed under isReturnDefaultValues).
    testImplementation("org.json:json:20240303")
}

android {
    // Stub Android APIs (android.util.Log etc.) called from production code
    // return default values inside JVM unit tests instead of throwing
    // "Method X not mocked". Lets YamlHardener / SubscriptionUpdateMerge
    // tests cover the real production code path including its log lines.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

afterEvaluate {
    android {
        libraryVariants.forEach {
            sourceSets[it.name].kotlin.srcDir(buildDir.resolve("generated/ksp/${it.name}/kotlin"))
            sourceSets[it.name].java.srcDir(buildDir.resolve("generated/ksp/${it.name}/java"))
        }
    }
}
