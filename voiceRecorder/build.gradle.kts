import org.gradle.api.JavaVersion

plugins {
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.androidLibrary)
}

kotlin {
	androidTarget()
	jvm()

	sourceSets {
		commonMain.dependencies {
			implementation(libs.kotlinx.io.core)
			implementation(libs.kotlinx.coroutinesCore)
			implementation(libs.napier)
		}
	}
}

android {
	namespace = "de.lehrbaum.voicerecorder"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		minSdk = libs.versions.android.minSdk.get().toInt()
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
}
