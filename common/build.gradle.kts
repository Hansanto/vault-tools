plugins {
    id("multiplatform-module")
    id("kotest-module")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            api(libs.kotlinx.coroutines.core)
            api(libs.clikt)
            api(libs.mordant)
            api(libs.mordant.coroutines)
            api(libs.ktor.server.core)
            api(libs.ktor.server.cio)
            api(libs.kault)
        }

        commonTest.dependencies {
            implementation(project(":common-test"))
        }

        jvmMain.dependencies {
            api(libs.slf4j.simple)
        }
    }
}
