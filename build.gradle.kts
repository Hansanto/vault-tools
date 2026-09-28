// Force test tasks to run sequentially (like --max-workers=1)
abstract class TestSerializerService : BuildService<BuildServiceParameters.None>

val testSerializerService = gradle.sharedServices.registerIfAbsent(
    "testSerializerService",
    TestSerializerService::class
) {
    maxParallelUsages.set(1)
}

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots")
    }

    pluginManager.apply("ktlint-module")

    tasks.withType<Test>().configureEach {
        usesService(testSerializerService)
    }
}

subprojects {
    pluginManager.apply("detekt-module")
}

tasks.register("detektAll") {
    group = JavaBasePlugin.VERIFICATION_GROUP
    description = "Run detekt on all projects"
    dependsOn(subprojects.map { "${it.path}:detekt" })
}

tasks.register("compileAll") {
    group = JavaBasePlugin.BUILD_TASK_NAME
    description = "Compile all projects"
    dependsOn(
        provider {
            subprojects.flatMap { project ->
                project.tasks.named { it.startsWith("compileTestKotlin") }
            }
        }
    )
}
