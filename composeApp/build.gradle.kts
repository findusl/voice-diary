import buildsrc.markBuiltInClassesAsStable
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import java.util.Properties
import org.gradle.api.tasks.testing.Test
import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.androidApplication)
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
val backendUrl = localProps.getProperty("backendUrl") ?: "http://localhost:8888"

// Classes treated as stable by the Compose compiler.
val stableClasses = listOf("kotlin.uuid.Uuid")

buildkonfig {
	packageName = "de.lehrbaum.voiry"
	exposeObjectWithName = "BuildKonfig"
	defaultConfigs {
		buildConfigField(STRING, "BACKEND_URL", backendUrl)
	}
}

kotlin {
	androidTarget {
		@OptIn(ExperimentalKotlinGradlePluginApi::class)
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_11)
			markBuiltInClassesAsStable(project, stableClasses)
		}
	}

	jvm {
		@OptIn(ExperimentalKotlinGradlePluginApi::class)
		compilerOptions { markBuiltInClassesAsStable(project, stableClasses) }
	}

	@OptIn(ExperimentalComposeLibrary::class)
	sourceSets {
		androidMain.dependencies {
			implementation(compose.preview)
			implementation(libs.androidx.activity.compose)
		}
		commonMain.dependencies {
			implementation(projects.shared)
			implementation(compose.runtime)
			implementation(compose.foundation)
			implementation(compose.material3)
			implementation(compose.ui)
			implementation(compose.components.resources)
			implementation(compose.components.uiToolingPreview)
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
		}
		commonTest.dependencies {
			implementation(libs.kotlin.test)
		}
		jvmMain.dependencies {
			implementation(compose.desktop.currentOs)
			implementation(libs.kotlinx.coroutinesSwing)
			implementation(libs.appdirs)
		}
		jvmTest.dependencies {
			implementation(compose.uiTest)
			implementation(compose.desktop.uiTestJUnit4)
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

android {
	namespace = "de.lehrbaum.voiry"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		applicationId = "de.lehrbaum.voiry"
		minSdk = libs.versions.android.minSdk.get().toInt()
		targetSdk = libs.versions.android.targetSdk.get().toInt()
		versionCode = 1
		versionName = "1.0"
	}
	packaging {
		resources {
			excludes += "/META-INF/{AL2.0,LGPL2.1}"
		}
	}
	buildTypes {
		getByName("release") {
			isMinifyEnabled = false
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
}

composeCompiler {
	reportsDestination = layout.buildDirectory.dir("compose_compiler")
	metricsDestination = layout.buildDirectory.dir("compose_compiler")
}

dependencies {
	debugImplementation(compose.uiTooling)
}

compose.desktop {
	application {
		mainClass = "de.lehrbaum.voiry.MainKt"

		nativeDistributions {
			targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
			packageName = "de.lehrbaum.voiry"
			packageVersion = "1.0.0"
		}
	}
}
