plugins {
	alias(libs.plugins.kotlinJvm)
	alias(libs.plugins.ktor)
	application
}

group = "de.lehrbaum.voiry"
version = "1.0.0"
application {
	mainClass.set("de.lehrbaum.voiry.ApplicationKt")

	val isDevelopment: Boolean = project.ext.has("development")
	applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
	implementation(projects.shared)
	implementation(libs.logback)
	implementation(libs.ktor.serverCore)
	implementation(libs.ktor.serverNetty)
	implementation(libs.ktor.serverContentNegotiation)
	implementation(libs.ktor.serializationKotlinxJson)
	implementation(libs.ktor.serverSse)
	implementation(libs.napier)
	testImplementation(libs.ktor.serverTestHost)
	testImplementation(libs.kotlin.testJunit)
	testImplementation(libs.ktor.clientCore)
	testImplementation(libs.ktor.clientCio)
	testImplementation(libs.ktor.clientContentNegotiation)
	testImplementation(libs.ktor.serializationKotlinxJson)
}
