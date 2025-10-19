import com.github.kr328.golang.GolangBuildTask
import com.github.kr328.golang.GolangPlugin

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
    id("golang-android")
}

val golangSource = file("src/main/golang/native")

android {

    @Suppress("UnstableApiUsage")
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    productFlavors.configureEach {
        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                arguments(
                    "-DGO_SOURCE:STRING=${golangSource.absolutePath}",
                    "-DGO_OUTPUT:STRING=${GolangPlugin.outputDirOf(project, null, null)}",
                    "-DFLAVOR_NAME:STRING=$name"
                )
            }
        }
    }
}

golang {
    sourceSets {
        register("alpha") {
            tags.set(listOf("foss", "with_gvisor", "cmfa"))
            srcDir.set(file("src/foss/golang"))
        }

        register("meta") {
            tags.set(listOf("foss", "with_gvisor", "cmfa"))
            srcDir.set(file("src/foss/golang"))
        }

        all {
            fileName.set("libclash.so")
            packageName.set("cfa/native")
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(libs.kotlin.coroutine)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.androidx.core)
}

tasks.withType<GolangBuildTask>().configureEach {
    inputs.dir(golangSource)
}

val abiMappings = mapOf(
    "arm64-v8a" to "Arm64V8a",
    "armeabi-v7a" to "ArmeabiV7a",
    "x86" to "X86",
    "x86_64" to "X8664"
)

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val buildType = if (variant.buildType == "debug") "Debug" else "RelWithDebInfo"

        abiMappings.forEach { (abi, goAbi) ->
            tasks.configureEach {
                // 确保 CMake 配置任务也依赖 Golang 构建
                if (name.startsWith("configureCMake$buildType[$abi]") ||
                    name.startsWith("buildCMake$buildType[$abi]")
                ) {
                    val golangTaskName = "externalGolangBuild$variantName$goAbi"
                    dependsOn(golangTaskName)
                }
            }
        }
    }
}
