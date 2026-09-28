import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
    id("io.kotest")
}

val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

// Set to true in modules that expose test utilities (e.g. common-test),
// so kotest dependencies are available as api on *Main source sets.
val exposeTestDependencies = project.findProperty("exposeTestDependencies") == "true"

kotlin {
    sourceSets {
        val common: NamedDomainObjectProvider<KotlinSourceSet>
        val jvm: NamedDomainObjectProvider<KotlinSourceSet>

        if (exposeTestDependencies) {
            common = commonMain
            jvm = jvmMain
        } else {
            common = commonTest
            jvm = jvmTest
        }

        common.dependencies {
            api(libs.findLibrary("kotest.framework.engine").get())
            api(libs.findLibrary("kotest.assertions.core").get())
            api(libs.findLibrary("kotest.assertions.json").get())
        }

        jvm.dependencies {
            api(libs.findLibrary("kotest.runner.junit5").get())
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
