import com.android.build.gradle.BaseExtension
import com.android.build.gradle.api.AndroidBasePlugin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.golang) apply false
}

allprojects {
    plugins.withType<AndroidBasePlugin>().configureEach {
        extensions.configure<BaseExtension> {
            namespace = "com.github.kr328.clash.${project.name}"
            defaultConfig {
                minSdk = 26
            }
            compileSdkVersion(36)
            ndkVersion = "29.0.14206865"
            flavorDimensions("feature")
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
                    java.srcDirs("src/foss/java")
                }
                getByName("alpha") {
                    java.srcDirs("src/foss/java")
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
