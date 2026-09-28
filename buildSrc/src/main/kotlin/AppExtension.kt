import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType

/**
 * Allows configuring the main class for the application plugin.
 * Use it with dsl:
 * ```kotlin
 * app {
 *     mainClass = "xxx"
 * }
 * ```
 */
abstract class AppExtension {
    /**
     * The main class of the application.
     */
    abstract val mainClass: Property<String>

    /**
     * The name of the module to create the final executable.
     * If empty, the final executable will be named with the root project name only.
     * Otherwise, the final executable will be named with the root project name and the module name, separated by a dash.
     */
    abstract val executableModuleName: Property<String>
}

/**
 * Gets the [AppExtension] from the project, or creates it if it doesn't exist.
 * @return the [AppExtension] instance.
 */
fun Project.getAppExtension(): AppExtension = extensions.findByType<AppExtension>()
    ?: extensions.create<AppExtension>("app").also {
        it.executableModuleName.convention(project.name)
    }

/**
 * Gets the name of the executable to create.
 * If the [AppExtension.executableModuleName] is not blank, the name will be in the format of "rootProjectName-moduleName".
 * Otherwise, the name will be the same as the root project name.
 *
 * @param rootProjectName Root project name to use as the base for the executable name.
 * @return Executable prefix to use for the final executable name.
 */
fun AppExtension.getExecutableName(rootProjectName: String): String {
    val moduleNameValue = executableModuleName.orNull?.takeIf { it.isNotBlank() }
    return if (moduleNameValue != null) {
        "$rootProjectName-$moduleNameValue"
    } else {
        rootProjectName
    }
}
