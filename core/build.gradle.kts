import io.github.goooler.golang.tasks.GoCompile.Companion.baseOutputDir as goBaseOutputDir

plugins {
    kotlin("android")
    id("com.android.library")
    id("kotlinx-serialization")
    alias(libs.plugins.golang)
}

val golangSource = file("src/main/golang/native")

go {
    buildTags = listOf("foss","with_gvisor","cmfa")
    outputFileName = "libclash.so"
    packageName = "cfa/native"
}

android {
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }
}

dependencies {
    implementation(project(":common"))

    implementation(libs.androidx.core)
    implementation(libs.kotlin.coroutine)
    implementation(libs.kotlin.serialization.json)
}

androidComponents.onVariants { variant ->
    variant.sources.getByName("go").addStaticSourceDirectory("src/foss/golang")
    variant.externalNativeBuild?.arguments?.addAll(
        "-DGO_SOURCE:STRING=${golangSource}",
        "-DGO_OUTPUT:STRING=${goBaseOutputDir.get().asFile}",
        "-DFLAVOR_NAME:STRING=${variant.flavorName.orEmpty()}"
    )
}
