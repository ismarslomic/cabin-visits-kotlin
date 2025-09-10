@file:Suppress("UNCHECKED_CAST")

package no.slomic.smarthytte.utils

/**
 * Temporarily sets environment variables for the duration of [block] and restores them afterwards.
 * This uses reflection hacks that work on modern JDKs when tests are started with:
 *  --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED
 */
suspend fun <T> withTestEnvironment(env: Map<String, String>, block: suspend () -> T): T {
    val previous = HashMap(System.getenv())
    try {
        setEnv(previous + env)
        return block()
    } finally {
        setEnv(previous)
    }
}

private fun setEnv(envVars: Map<String, String>) {
    try {
        val processEnvironment = Class.forName("java.lang.ProcessEnvironment")
        val theEnvironmentField = processEnvironment.getDeclaredField("theEnvironment")
        theEnvironmentField.isAccessible = true
        val env = theEnvironmentField.get(null) as MutableMap<String, String>
        env.clear()
        env.putAll(envVars)
        val theCaseInsensitiveEnvironmentField =
            processEnvironment.getDeclaredField("theCaseInsensitiveEnvironment")
        theCaseInsensitiveEnvironmentField.isAccessible = true
        val cienv = theCaseInsensitiveEnvironmentField.get(null) as MutableMap<String, String>
        cienv.clear()
        cienv.putAll(envVars)
        return
    } catch (_: Throwable) {
        // Fallback for other JVMs: modify the unmodifiable map inside System.getenv()
    }

    val classes = Class.forName("java.util.Collections")
    val field = classes.declaredClasses.firstOrNull { it.simpleName == "UnmodifiableMap" }
        ?: error("Collections.UnmodifiableMap class not found")
    val m = System.getenv()
    val mapField = field.getDeclaredField("m")
    mapField.isAccessible = true
    val obj = mapField.get(m) as MutableMap<String, String>
    obj.clear()
    obj.putAll(envVars)
}
