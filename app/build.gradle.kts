plugins {
    id("multiplatform-module")
    id("shadow-module")
}

app {
    mainClass = "io.github.hansanto.vaulttools.app.Main"
    executableModuleName = ""
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":search"))
            api(libs.ktor.client.core)
            api(libs.ktor.serialization)
            api(libs.ktor.serialization.kotlinx.json)
            api(libs.ktor.client.content.negotiation)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }

        jsMain.dependencies {
            implementation(libs.ktor.client.js)
        }

        appleMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        linuxMain.dependencies {
            implementation(libs.ktor.client.curl)
        }

        mingwMain.dependencies {
            implementation(libs.ktor.client.winhttp)
        }
    }
}
