plugins {
    id("multiplatform-module")
    id("kotest-module")
    id("resources-module")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":common"))
        }

        commonTest.dependencies {
            implementation(project(":common-test"))
        }
    }
}
