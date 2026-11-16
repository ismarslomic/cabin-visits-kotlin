package no.slomic.smarthytte.properties

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaConstructor

private const val METADATA = "META-INF/native-image/main/reachability-metadata.json"

/** The constructor of every properties class, as `class(type, type)`, from the compiled classes. */
private fun compiledConstructors(): Map<String, List<String>> {
    // Any class of the package, to find the directory of the compiled main classes
    val marker = DatabaseProperties::class.java
    val directory = File(marker.protectionDomain.codeSource.location.toURI())
        .resolve(marker.packageName.replace('.', '/'))

    return directory.listFiles { file -> file.name.endsWith(".class") && !file.name.contains('$') }
        .orEmpty()
        .map { Class.forName("${marker.packageName}.${it.nameWithoutExtension}") }
        .filter { type -> type.kotlin.isData }
        .associate { type ->
            // The primary constructor: a class with default values also has a synthetic constructor
            val constructor = checkNotNull(type.kotlin.primaryConstructor?.javaConstructor)
            type.name to constructor.parameterTypes.map { it.name }
        }
}

/** The `<init>` parameter types that `reachability-metadata.json` registers for each class. */
private fun registeredConstructors(): Map<String, List<List<String>>> {
    val reflection = Json.parseToJsonElement(File(METADATA).readText()).jsonObject.getValue("reflection").jsonArray

    // An entry whose type is an object is not a class name (for example a proxy)
    return reflection.map { it.jsonObject }.filter { it["type"] is JsonPrimitive }.associate { entry ->
        entry.getValue("type").jsonPrimitive.content to
            (entry["methods"] as? JsonArray).orEmpty().map { it.jsonObject }
                .filter { it.getValue("name").jsonPrimitive.content == "<init>" }
                .map { constructor -> constructor.parameterTypes() }
    }
}

private fun JsonObject.parameterTypes(): List<String> =
    getValue("parameterTypes").jsonArray.map { it.jsonPrimitive.content }

class PropertiesReachabilityTest :
    StringSpec({
        "every properties class should be registered for reflection with its current constructor" {
            // Hoplite builds these classes by reflection. In the native image an unregistered class, or a constructor
            // that changed after the registration, fails at startup with MissingReflectionRegistrationError.
            val registered = registeredConstructors()
            val compiled = compiledConstructors()
            check(compiled.size > 10) { "Found only ${compiled.size} properties classes, is the scan broken?" }

            val problems = compiled.mapNotNull { (type, parameters) ->
                val constructors = registered[type]
                when {
                    constructors == null -> "$type is not registered"
                    parameters !in constructors -> "$type has the constructor $parameters, registered: $constructors"
                    else -> null
                }
            }

            problems.shouldBeEmpty()
        }
    })
