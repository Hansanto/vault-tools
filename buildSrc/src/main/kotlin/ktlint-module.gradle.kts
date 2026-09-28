import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

ktlint {
    // https://github.com/pinterest/ktlint/releases/tag/1.8.0
    // Forced to support context parameter
    // https://kotlinlang.org/docs/context-parameters.html#context-parameters-resolution
    version.set("1.8.0")
}

configure<KtlintExtension> {
    ignoreFailures.set(false)
    filter {
        exclude {
            "/build/" in it.file.path
        }
        include("**/*.kt")
    }
    reporters {
        reporter(ReporterType.HTML)
        reporter(ReporterType.CHECKSTYLE)
    }
}
