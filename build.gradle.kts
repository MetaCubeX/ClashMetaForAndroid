import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.api.AndroidBasePlugin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.legacyKapt) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.golang) apply false
}

allprojects {
    plugins.withType<AndroidBasePlugin>().configureEach {
        extensions.configure<CommonExtension> {
            namespace = "com.github.kr328.clash.${project.name}"
            compileSdk = 36
            defaultConfig.apply {
                minSdk = 26
            }
            ndkVersion = "29.0.14206865"
            flavorDimensions += "feature"
            productFlavors {
                create("alpha") {
                    dimension = "feature"
                }
                create("meta") {
                    dimension = "feature"
                }
            }
            sourceSets {
                getByName("meta") {
                    java.directories.add("src/foss/java")
                }
                getByName("alpha") {
                    java.directories.add("src/foss/java")
                }
            }
            compileOptions.apply {
                sourceCompatibility(libs.versions.jvmTarget.get())
                targetCompatibility(libs.versions.jvmTarget.get())
            }
        }
    }

    plugins.withType<JavaBasePlugin>().configureEach {
        extensions.configure<JavaPluginExtension> {
            setSourceCompatibility(libs.versions.jvmTarget.get())
            setTargetCompatibility(libs.versions.jvmTarget.get())
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())
        }
    }
}
