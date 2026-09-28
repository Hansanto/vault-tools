plugins {
    id("multiplatform-module")
    id("kotest-module")
    id("resources-module")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            api(libs.kault)
            api(libs.kotlinx.coroutines.test)
            api(libs.ktor.client.cio)
            api(libs.ktor.client.logging)
            api(libs.mordant)
            api(libs.mordant.coroutines)
        }
        jvmMain.dependencies {
            api(libs.playwright.jvm)
        }
    }
}

tasks {
    // https://playwright.dev/java/docs/codegen#running-codegen
    register<JavaExec>("codegen") {
        group = "playwright"
        description = "Launches Playwright codegen tool"
        classpath = sourceSets.jvmMain.get().runtimeClasspath
        mainClass.set("com.microsoft.playwright.CLI")
        args("codegen", "--viewport-size=1920,1080")
    }

    // https://playwright.dev/java/docs/cli#installing-browsers
    register<JavaExec>("install") {
        group = "playwright"
        description = "Installs Playwright browsers"
        classpath = sourceSets.jvmMain.get().runtimeClasspath
        mainClass.set("com.microsoft.playwright.CLI")
        args("install")
    }
}
