import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import java.util.Properties
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.androidMultiplatformLibrary)
	alias(libs.plugins.composeMultiplatform)
	alias(libs.plugins.composeCompiler)
	alias(libs.plugins.composeHotReload)
	alias(libs.plugins.kotlinSerialization)
	alias(libs.plugins.mokkery)
	alias(libs.plugins.buildkonfig)
}

val localProps = Properties().apply {
	val f = rootProject.file("local.properties")
	if (f.exists()) f.inputStream().use { load(it) }
}
val backendUrl = localProps.getProperty("desktopBackendUrl")
	?: localProps.getProperty("backendUrl")
	?: "http://localhost:8888"

buildkonfig {
	packageName = "de.lehrbaum.voiry"
	exposeObjectWithName = "BuildKonfig"
	defaultConfigs {
		buildConfigField(STRING, "BACKEND_URL", backendUrl)
	}
}

kotlin {
	android {
		namespace = "de.lehrbaum.voiry.composeapp"
		compileSdk = libs.versions.android.compileSdk.get().toInt()
		minSdk = libs.versions.android.minSdk.get().toInt()
		@OptIn(ExperimentalKotlinGradlePluginApi::class)
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_17)
		}
	}

	jvm {
		@OptIn(ExperimentalKotlinGradlePluginApi::class)
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_17)
		}
	}

	jvmToolchain(21)

	sourceSets {
		commonMain.dependencies {
			implementation(libs.wav.recorder)
			implementation(projects.shared)
			implementation(libs.compose.runtime)
			implementation(libs.compose.foundation)
			implementation(libs.compose.material3)
			implementation(libs.compose.ui)
			implementation(libs.compose.ui.tooling.preview)
			implementation(libs.androidx.lifecycle.viewmodelCompose)
			implementation(libs.androidx.lifecycle.runtimeCompose)
			implementation(libs.kotlinx.io.core)
			implementation(libs.kotlinx.serializationJson)
			implementation(libs.ktor.clientCore)
			implementation(libs.ktor.clientContentNegotiation)
			implementation(libs.ktor.serializationKotlinxJson)
			implementation(libs.ktor.clientCio)
			api(libs.napier)
			implementation(libs.lexilabs.basic.sound)
			implementation(libs.kotlinx.datetime)
			implementation(libs.kotlinx.collections.immutable)
			implementation(libs.appdirs)
		}
		commonTest.dependencies {
			implementation(libs.kotlin.test)
			implementation(libs.kotlinx.coroutinesTest)
		}
		jvmMain.dependencies {
			implementation(compose.desktop.currentOs)
			implementation(libs.kotlinx.coroutinesSwing)
		}
		jvmTest.dependencies {
			implementation(libs.compose.ui.test)
			implementation(libs.compose.ui.test.junit4.desktop)
			implementation(libs.junit)
			implementation(projects.server)
			implementation(libs.ktor.serverTestHost)
			implementation(libs.ktor.serverContentNegotiation)
			implementation(libs.ktor.serializationKotlinxJson)
			implementation(libs.ktor.serverSse)
			implementation(libs.ktor.serverNetty)
			implementation(libs.logback)
			implementation(libs.mokkery.runtime)
		}
	}
}

val jvmTest by tasks.existing(Test::class)

tasks.register<Test>("jvmNoUiTest") {
	group = "verification"
	description = "Runs JVM tests excluding UI tests"
	testClassesDirs = jvmTest.get().testClassesDirs
	classpath = jvmTest.get().classpath
	useJUnit {
		excludeCategories("de.lehrbaum.voiry.UiTest")
	}
}

composeCompiler {
	reportsDestination = layout.buildDirectory.dir("compose_compiler")
	metricsDestination = layout.buildDirectory.dir("compose_compiler")
	stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("config/compose/stability.conf"))
}

compose.desktop {
	application {
		mainClass = "de.lehrbaum.voiry.MainKt"

		nativeDistributions {
			targetFormats(TargetFormat.Dmg)
			packageName = "de.lehrbaum.voiry"
			packageVersion = "1.0.0"
		}
	}
}
