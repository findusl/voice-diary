import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.androidLibrary)
	alias(libs.plugins.kotlinSerialization)
	alias(libs.plugins.purity)
}

kotlin {
	androidTarget {
		@OptIn(ExperimentalKotlinGradlePluginApi::class)
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_11)
		}
	}

	jvm()

	sourceSets {
		commonMain.dependencies {
			implementation(libs.kotlinx.coroutinesCore)
			implementation(libs.kotlinx.serializationJson)
			implementation(libs.purity.annotations)
		}
		commonTest.dependencies {
			implementation(libs.kotlin.test)
		}
	}
}

android {
	namespace = "de.lehrbaum.voiry.shared"
	compileSdk = libs.versions.android.compileSdk.get().toInt()
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
	defaultConfig {
		minSdk = libs.versions.android.minSdk.get().toInt()
	}
}
