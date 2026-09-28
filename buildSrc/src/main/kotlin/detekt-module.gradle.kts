import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    id("io.gitlab.arturbosch.detekt")
}

tasks {
    withType<DetektCreateBaselineTask>().configureEach {
        jvmTarget = JavaConstant.JVM_VERSION_STRING
    }

    withType<Detekt> {
        reports {
            html.required.set(true)
            xml.required.set(true)
        }
        jvmTarget = JavaConstant.JVM_VERSION_STRING
    }
}

pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    val kotlin = extensions.getByType<KotlinMultiplatformExtension>()
    tasks.withType<Detekt>().configureEach {
        source(kotlin.sourceSets.flatMap { it.kotlin.srcDirs })
    }
}
