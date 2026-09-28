plugins {
    id("multiplatform-module")
    id("kotest-module")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }

        jsMain.dependencies {
            api(npm("open", "11.0.4"))
        }
    }
}
