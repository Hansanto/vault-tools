import org.jetbrains.kotlin.gradle.dsl.JvmTarget

object JavaConstant {
    val JVM_VERSION = JvmTarget.JVM_17
    val JVM_VERSION_STRING = JVM_VERSION.target
    val JVM_VERSION_NUMBER = JVM_VERSION_STRING.toInt()
}
