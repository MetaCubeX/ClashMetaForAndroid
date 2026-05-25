import android.databinding.tool.ext.capitalizeUS
import com.github.kr328.golang.GolangBuildTask
import com.github.kr328.golang.GolangPlugin

plugins {
    kotlin("android")
    id("com.android.library")
    id("kotlinx-serialization")
    id("golang-android")
}

val golangSource = file("src/main/golang/native")

// Run pure-Go unit tests in the snapshot package before any Java/Kotlin
// compile. Snapshot is the engine-delegated read path for ClashFest UI
// (see docs/path-b-engine-parsing.md); we cannot afford it to silently
// regress when we change the Go-side data shape or mihomo upgrades.
//
// The package is intentionally isolated from cfa/native/app, so 'go test'
// works on any developer workstation (Windows/macOS/Linux) without
// platform stubs or NDK.
val goTestNativeSnapshot by tasks.registering(Exec::class) {
    description = "Run go unit tests for the native snapshot package"
    group = "verification"
    workingDir = file("src/main/golang")
    commandLine("go", "test", "./native/snapshot/...")

    inputs.dir("src/main/golang/native/snapshot")
    val marker = layout.buildDirectory.file("go-tests/snapshot.passed")
    outputs.file(marker)
    doLast {
        marker.get().asFile.apply {
            parentFile.mkdirs()
            writeText("passed at ${System.currentTimeMillis()}\n")
        }
    }
}

tasks.withType(JavaCompile::class).configureEach {
    dependsOn(goTestNativeSnapshot)
}

golang {
    sourceSets {
        create("alpha") {
            tags.set(listOf("foss","with_gvisor","cmfa"))
            srcDir.set(file("src/foss/golang"))
        }
        create("meta") {
            tags.set(listOf("foss","with_gvisor","cmfa"))
            srcDir.set(file("src/foss/golang"))
        }
        all {
            fileName.set("libclash.so")
            packageName.set("cfa/native")
        }
    }
}

android {
    productFlavors {
        all {
            externalNativeBuild {
                cmake {
                    arguments("-DGO_SOURCE:STRING=${golangSource}")
                    arguments("-DGO_OUTPUT:STRING=${GolangPlugin.outputDirOf(project, null, null)}")
                    arguments("-DFLAVOR_NAME:STRING=$name")
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    implementation(project(":common"))

    implementation(libs.androidx.core)
    implementation(libs.kotlin.coroutine)
    implementation(libs.kotlin.serialization.json)
}

afterEvaluate {
    tasks.withType(GolangBuildTask::class.java).forEach {
        val task = it
        task.inputs.dir(golangSource)
        task.doFirst {
            val command = task.commandLine.map { argument: Any -> argument.toString() }.toMutableList()
            val tagsIndex = command.indexOf("-tags")
            if (tagsIndex >= 0 && tagsIndex + 1 < command.size) {
                val tags = command[tagsIndex + 1]
                    .split(",")
                    .filter { tag: String -> tag != "debug" }

                if (tags.isEmpty()) {
                    command.removeAt(tagsIndex + 1)
                    command.removeAt(tagsIndex)
                } else {
                    command[tagsIndex + 1] = tags.joinToString(",")
                }

                task.commandLine(command)
            }
        }
    }
}

val abis = listOf("arm64-v8a" to "Arm64V8a", "armeabi-v7a" to "ArmeabiV7a", "x86" to "X86", "x86_64" to "X8664")

androidComponents.onVariants { variant ->
    val cmakeName = if (variant.buildType == "debug") "Debug" else "RelWithDebInfo"

    abis.forEach { (abi, goAbi) ->
        tasks.configureEach {
            if (name.startsWith("buildCMake$cmakeName[$abi]")) {
                dependsOn("externalGolangBuild${variant.name.capitalizeUS()}$goAbi")
                println("Set up dependency: $name -> externalGolangBuild${variant.name.capitalizeUS()}$goAbi")
            }
        }
    }
}
