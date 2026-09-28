@file:OptIn(KotlinNativeCacheApi::class)

import java.net.URI
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.DisableCacheInKotlinVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCacheApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

val appExtension = getAppExtension()

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(JavaConstant.JVM_VERSION_NUMBER)

    jvm {
        compilerOptions {
            jvmTarget = JavaConstant.JVM_VERSION
        }

        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }

    js {
        nodejs {
            passCliArgumentsToMainFunction()
        }
        binaries.executable()
        compilerOptions {
            this.sourceMap = false
        }
    }

    // wasmJs()
    // wasmWasi()

    // Native tiers: https://kotlinlang.org/docs/native-target-support.html
    // Tier 1
    macosArm64()
    iosSimulatorArm64()
    iosArm64()

    // Tier 2
    linuxX64()
    linuxArm64()
    // watchosSimulatorArm64()
    // watchosArm32()
    // watchosArm64()
    // tvosSimulatorArm64()
    // tvosArm64()

    // Tier 3
    mingwX64()
    // watchosDeviceArm64()
    // iosX64()

    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.contracts.ExperimentalContracts")
                optIn("kotlinx.serialization.ExperimentalSerializationApi")
                optIn("kotlin.ExperimentalStdlibApi")
                optIn("kotlin.time.ExperimentalTime")
                optIn("kotlinx.coroutines.DelicateCoroutinesApi")
                optIn("kotlin.concurrent.atomics.ExperimentalAtomicApi")
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        // Used to bundle the compiled JS executable (and its npm dependencies) into a single
        // self-contained file (see tasks below).
        jsMain.dependencies {
            implementation(devNpm("esbuild", "0.28.2"))
        }
    }
}


afterEvaluate {
    // Apply native cache disabling to all native targets for all modules
    // This must be done early, before the appMainClass check
    kotlin.targets.withType<KotlinNativeTarget> {
        binaries.all {
            disableNativeCache(
                version = DisableCacheInKotlinVersion.`2_4_20`,
                reason = "Clikt has duplicate symbol",
                issueUrl = URI("https://github.com/ajalt/clikt/issues/598")
            )
        }
    }

    val appMainClass = appExtension.mainClass.orNull ?: return@afterEvaluate
    val executableName = appExtension.getExecutableName(rootProject.name)
    val nativeAppMainClass = appMainClass.substringBeforeLast('.') + ".main"

    kotlin.jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        binaries {
            executable {
                mainClass.set(appMainClass)
            }
        }
    }

    kotlin.targets.withType<KotlinNativeTarget> {
        binaries {
            executable {
                entryPoint = nativeAppMainClass
                baseName = "${executableName}-${targetName}-${rootProject.version}"
            }
        }
    }

    val jsExecutableName = "${executableName}-${rootProject.version}"
    kotlin.js {
        outputModuleName = jsExecutableName
    }

    fun registerJsBundleTask(
        taskName: String,
        compileTaskName: String,
        executableDirName: String
    ) = tasks.register<Exec>(taskName) {
        group = "kotlin browser"
        description = "Bundles the compiled JS output and its npm dependencies into a single file using esbuild."
        dependsOn(compileTaskName)

        val inputFile = layout.buildDirectory.file(
            "compileSync/js/main/$executableDirName/kotlin/$jsExecutableName.js"
        )
        inputs.file(inputFile)

        val outputFile = layout.buildDirectory.file(
            "dist/js/$executableDirName/$jsExecutableName.js"
        )
        outputs.file(outputFile)

        val nodeModulesDir = rootProject.layout.buildDirectory.dir("js/node_modules").get()
        // esbuild resolves imports (e.g. "open") by walking up from the input file's
        // directory looking for `node_modules`.
        // The compiled file has no such ancestor, so we need to point it at the root project's shared
        // `build/js/node_modules` via NODE_PATH.
        environment("NODE_PATH", nodeModulesDir.asFile.absolutePath)

        commandLine(
            buildList {
                add("node")
                add(nodeModulesDir.file("esbuild/bin/esbuild").asFile.absolutePath)
                add(inputFile.get().asFile.absolutePath)
                add("--bundle")
                add("--platform=node")
                add("--outfile=${outputFile.get().asFile.absolutePath}")
            }
        )
    }

    val bundleDevelopmentExecutableJs = registerJsBundleTask(
        taskName = "bundleDevelopmentExecutableJs",
        compileTaskName = "compileDevelopmentExecutableKotlinJs",
        executableDirName = "developmentExecutable"
    )

    val bundleProductionExecutableJs = registerJsBundleTask(
        taskName = "bundleProductionExecutableJs",
        compileTaskName = "compileProductionExecutableKotlinJs",
        executableDirName = "productionExecutable"
    )

    // When running `./gradlew assemble`, bundle the JS executable for both development and production.
    tasks.named("assemble") {
        dependsOn(bundleProductionExecutableJs, bundleDevelopmentExecutableJs)
    }
}
