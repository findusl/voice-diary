import io.gitlab.arturbosch.detekt.Detekt

plugins {
	// this is necessary to avoid the plugins to be loaded multiple times
	// in each subproject's classloader
	alias(libs.plugins.androidApplication) apply false
	alias(libs.plugins.androidMultiplatformLibrary) apply false
	alias(libs.plugins.composeHotReload) apply false
	alias(libs.plugins.composeMultiplatform) apply false
	alias(libs.plugins.composeCompiler) apply false
	alias(libs.plugins.kotlinJvm) apply false
	alias(libs.plugins.kotlinMultiplatform) apply false
	alias(libs.plugins.mokkery) apply false
	alias(libs.plugins.ktlint)
	alias(libs.plugins.detekt)
}

allprojects {
	apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
	apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

	ktlint {
		filter {
			exclude("**/generated/**")
			exclude("**/BuildKonfig.kt")
		}
	}

	detekt {
		config.setFrom("$rootDir/config/detekt/detekt.yml")
	}

	tasks.withType<Detekt>().configureEach {
		jvmTarget = "17"
	}
}

tasks.register("checkAgentsEnvironment") {
	group = "verification"
	description = "Runs all tests that are expected to pass in the agent environment"
	dependsOn(
		":composeApp:jvmNoUiTest",
		":androidApp:testDebugUnitTest",
		":shared:jvmTest",
		":server:test",
	)
	// Also depend on ktlintCheck in every subproject, not just the root project
	dependsOn(subprojects.map { "${it.path}:ktlintCheck" })
	dependsOn(subprojects.map { "${it.path}:detekt" })
}
