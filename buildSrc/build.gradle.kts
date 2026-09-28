plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.gradle.plugin.kotlin)
    implementation(libs.gradle.plugin.kotlin.serialization)
    implementation(libs.gradle.plugin.shadow)
    implementation(libs.gradle.plugin.ktlint)
    implementation(libs.gradle.plugin.detekt)
    implementation(libs.gradle.plugin.kotest)
    implementation(libs.gradle.plugin.ksp)
    implementation(libs.gradle.plugin.resources)
}
