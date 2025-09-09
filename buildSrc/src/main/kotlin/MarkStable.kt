package buildsrc

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/**
 * Configures the Compose compiler to treat the provided classes as stable by generating
 * a stability configuration file under the root build directory and wiring it into the
 * compiler arguments for all Kotlin compilation tasks.
 */
fun KotlinJvmCompilerOptions.markBuiltInClassesAsStable(
    project: Project,
    stableClasses: List<String>,
) {
    val configFile = project.rootProject.layout.buildDirectory
        .file("compose_compiler_config.conf").get().asFile

    val task = project.rootProject.tasks.findByName("generateComposeStabilityConfig")
        ?: project.rootProject.tasks.register("generateComposeStabilityConfig") {
            inputs.property("stableClasses", stableClasses)
            outputs.file(configFile)
            doLast {
                configFile.parentFile.mkdirs()
                configFile.writeText(stableClasses.joinToString(System.lineSeparator()))
            }
            notCompatibleWithConfigurationCache("Task uses build script values")
        }.get()

    project.tasks.withType(KotlinCompilationTask::class.java).configureEach { dependsOn(task) }

    freeCompilerArgs.addAll(
        listOf(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:stabilityConfigurationPath=${configFile.absolutePath}",
        )
    )
}
