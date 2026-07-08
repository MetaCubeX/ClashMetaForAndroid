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

// The mihomo submodule HEAD, tracked as a task input. The Go builds compile
// `src/foss/golang` (module `foss`, `replace mihomo => ./clash`), but the golang
// tasks historically only tracked `src/main/golang/native` — so bumping the
// submodule left every golang task UP-TO-DATE and shipped a stale libclash.so.
// Tracking the commit (not the whole tree: ~6k files, and a plain checkout never
// mutates them) is enough to invalidate on every core bump.
val mihomoHead = providers.exec {
    commandLine("git", "-C", file("src/foss/golang/clash").absolutePath, "rev-parse", "HEAD")
}.standardOutput.asText.map { it.trim() }

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
    // Same build tags Gradle uses to compile the native libclash.so (see the
    // golang { } block below). config.ParseRawConfig pulls in symbols that
    // only exist under these tags (temporaryUpdateGeneral, etc), so go test
    // fails with "relocation target ... not defined" without them.
    commandLine("go", "test", "-tags", "foss,with_gvisor,cmfa", "./native/snapshot/...")

    inputs.dir("src/main/golang/native/snapshot")
    // Re-run against a bumped core too: the test exercises mihomo itself
    // (module `cfa`, `replace mihomo => ../../foss/golang/clash`).
    inputs.property("mihomoCommit", mihomoHead)
    inputs.files("src/main/golang/go.mod", "src/main/golang/go.sum")
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":common"))

    implementation(libs.androidx.core)
    implementation(libs.kotlin.coroutine)
    implementation(libs.kotlin.serialization.json)

    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
}

afterEvaluate {
    tasks.withType(GolangBuildTask::class.java).forEach {
        val task = it
        task.inputs.dir(golangSource)
        task.inputs.property("mihomoCommit", mihomoHead)
        task.inputs.files("src/foss/golang/go.mod", "src/foss/golang/go.sum")
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
