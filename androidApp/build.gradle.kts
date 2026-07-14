import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.androidApplication)
	alias(libs.plugins.composeMultiplatform)
	alias(libs.plugins.composeCompiler)
}

val localProps = Properties().apply {
	val file = rootProject.file("local.properties")
	if (file.exists()) file.inputStream().use { load(it) }
}
val backendUrl = localProps.getProperty("androidBackendUrl")
	?: localProps.getProperty("backendUrl")
	?: "http://10.0.2.2:8888"

android {
	namespace = "de.lehrbaum.voiry"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		applicationId = "de.lehrbaum.voiry"
		minSdk = libs.versions.android.minSdk.get().toInt()
		targetSdk = libs.versions.android.targetSdk.get().toInt()
		versionCode = 1
		versionName = "1.0"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		buildConfigField("String", "BACKEND_URL", "\"${backendUrl.replace("\"", "\\\"")}\"")
	}

	buildFeatures {
		buildConfig = true
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
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
	testOptions {
		animationsDisabled = true
	}
}

kotlin {
	compilerOptions {
		jvmTarget.set(JvmTarget.JVM_17)
	}
}

dependencies {
	implementation(projects.composeApp)
	implementation(libs.androidx.activity.compose)
	implementation(libs.appdirs)
	implementation(libs.compose.material3)
	implementation(libs.compose.ui.tooling.preview)
	debugImplementation(libs.compose.ui.tooling)

	androidTestImplementation(libs.androidx.test.core)
	androidTestImplementation(libs.androidx.test.ext.junit)
	androidTestImplementation(libs.androidx.test.rules)
	androidTestImplementation(libs.androidx.test.runner)
	androidTestImplementation(libs.compose.ui.test.junit4)
}
