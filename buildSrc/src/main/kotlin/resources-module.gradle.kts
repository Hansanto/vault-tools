import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    kotlin("multiplatform")
    id("com.goncalossilva.resources")
}

val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

// Set to true in modules that expose test utilities (e.g. common-test),
// so kotest dependencies are available as api on *Main source sets.
val exposeTestDependencies = project.findProperty("exposeTestDependencies") == "true"

kotlin {
    sourceSets {
        val common: NamedDomainObjectProvider<KotlinSourceSet> = if (exposeTestDependencies) {
            commonMain
        } else {
            commonTest
        }

        common.dependencies {
            api(libs.findLibrary("resources").get())
        }
    }
}
