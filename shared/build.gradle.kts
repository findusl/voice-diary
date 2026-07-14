import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.androidMultiplatformLibrary)
	alias(libs.plugins.kotlinSerialization)
}

kotlin {
	android {
		namespace = "de.lehrbaum.voiry.shared"
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
			implementation(libs.kotlinx.serializationJson)
		}
		commonTest.dependencies {
			implementation(libs.kotlin.test)
		}
	}
}
