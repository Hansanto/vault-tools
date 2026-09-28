import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.gradleup.shadow")
}

val appExtension = getAppExtension()

afterEvaluate {
    val appMainClass = appExtension.mainClass.orNull ?: error(
        """
            You must specify the main class for the application in order to use the shadow-module plugin.
            Please add the following to your build.gradle.kts:
            app {
                mainClass = "your.main.Class"
            }
        """.trimIndent()
    )
    val executableName = appExtension.getExecutableName(rootProject.name)

    tasks.withType<ShadowJar> {
//        minimize {
//            exclude(dependency("com.github.ajalt.clikt:.*:.*"))
//            // Ktor engines (CIO, ...) are loaded at runtime through java.util.ServiceLoader,
//            // so they have no compile-time reference and would be stripped by `minimize`.
//            exclude(dependency("io.ktor:.*:.*"))
//            // Same for the coroutines/slf4j service-loader based providers used by Ktor.
//            exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-.*:.*"))
//            exclude(dependency("org.slf4j:.*:.*"))
//        }
//        // Concatenate META-INF/services/* entries instead of letting them overwrite each other.
//        mergeServiceFiles()
        archiveClassifier = ""
        archiveVersion
        archiveBaseName = executableName
        manifest {
            attributes["Main-Class"] = appMainClass
        }
    }
}
