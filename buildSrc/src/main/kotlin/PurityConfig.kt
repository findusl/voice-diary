package buildsrc

import org.gradle.api.Project

/**
 * Shared purity configuration applied to every module using the Purity plugin.
 */
fun Project.configureVoiceDiaryPurity() {
    val purityExtension = extensions.findByName("purity") ?: return
    purityExtension.applyVoiceDiaryDefaults()
}

private fun Any.applyVoiceDiaryDefaults() {
    fun update(property: String, values: Set<String>) {
        val capitalized = property.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val getter = javaClass.getMethod("get$capitalized")
        val setter = javaClass.getMethod("set$capitalized", Set::class.java)
        val current = getter.invoke(this) as? Set<*> ?: emptySet<Any?>()
        val combined = LinkedHashSet<String>(current.size + values.size)
        current.forEach { combined.add(it.toString()) }
        combined.addAll(values)
        setter.invoke(this, combined)
    }

    update(
        property = "wellKnownReadonlyFunctions",
        values = setOf(
            "ca.gosyer.appdirs.AppDirs.getUserCacheDir",
            "ca.gosyer.appdirs.AppDirs.getUserDataDir",
            "io.ktor.client.statement.bodyAsText",
            "io.ktor.http.HttpStatusCode.isSuccess",
			"io.ktor.http.isSuccess",
        ),
    )

    update(
        property = "wellKnownReadonlyClasses",
        values = setOf(
            "kotlinx.collections.immutable.PersistentCollection",
            "kotlinx.collections.immutable.PersistentList",
        ),
    )

    update(
        property = "wellKnownPureFunctions",
        values = setOf(
            "kotlin.collections.any",
            "kotlin.collections.filterNot",
            "kotlin.collections.map",
            "kotlinx.collections.immutable.ExtensionsKt.toPersistentList",
            "kotlinx.collections.immutable.PersistentList.add",
            "kotlinx.collections.immutable.toPersistentList",
			"kotlin.time.Instant\$Companion.fromEpochMilliseconds",
			"kotlin.time.Instant.Companion.fromEpochMilliseconds",
        ),
    )
}
