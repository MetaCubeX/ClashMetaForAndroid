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
    defaultConfig {
        externalNativeBuild {
            cmake {
                // Support 16 KB page sizes.
                arguments("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            }
        }
    }

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
        it.inputs.dir(golangSource)
        it.support16KbSizes()
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

// TODO: upstream these fixes to https://github.com/Kr328/golang-gradle-plugin.
fun GolangBuildTask.support16KbSizes() {
    val args = args?.toMutableList() ?: return
    if (args.isEmpty()) return
    val packageName = args.removeLast()

    // Support 16 KB page sizes.
    val newLdFlags = "-linkmode=external -extldflags=-Wl,-z,max-page-size=16384"

    val ldFlagsIndex = args.indexOf("-ldflags")
    if (ldFlagsIndex != -1 && ldFlagsIndex + 1 < args.size) {
        val existingLdFlags = args[ldFlagsIndex + 1]
        args[ldFlagsIndex + 1] = "$existingLdFlags $newLdFlags"
    } else {
        args.add("-ldflags=$newLdFlags")
    }

    args.add(packageName)
    this.args = args
}