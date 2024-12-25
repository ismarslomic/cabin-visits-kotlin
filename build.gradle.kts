import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

val logbackVersion: String by project
val hopliteVersion: String by project
val prometheusVersion: String by project

plugins {
    application
    kotlin("jvm") version "2.1.0"
    id("io.ktor.plugin") version "3.0.3"
    id("com.github.ben-manes.versions") version "0.51.0" // plugin for checking outdated deps
    id("org.graalvm.buildtools.native") version "0.10.4"
}

group = "no.slomic.smarthytte"
version = "0.0.1"

application {
    mainClass.set("no.slomic.smarthytte.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-cio-jvm")

    implementation("io.ktor:ktor-server-metrics-micrometer-jvm")
    implementation("io.micrometer:micrometer-registry-prometheus:$prometheusVersion")

    implementation("com.sksamuel.hoplite:hoplite-yaml:$hopliteVersion")

    testImplementation("io.ktor:ktor-server-test-host-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

// See https://graalvm.github.io/native-build-tools/0.10.4/gradle-plugin.html
graalvmNative {
    val resourcePath = "$projectDir/META-INF/native-image"

    binaries {

        named("main") {
            fallback = false // Sets the fallback mode of native-image, defaults to false
            verbose = true // Add verbose output, defaults to false
            imageName.set("graalvm-server")

            // Support adding additional build arguments as CLI argument -PnativeBuildArgs
            val additionalArgs: List<String> = project.findProperty("nativeBuildArgs")?.toString()?.split(",") ?: emptyList()
            buildArgs.addAll(additionalArgs)
            buildArgs.addAll(
                "--initialize-at-build-time=ch.qos.logback",
                "--initialize-at-build-time=io.ktor,kotlin",
                "--initialize-at-build-time=kotlinx.coroutines.CoroutineName",
                "--initialize-at-build-time=kotlinx.coroutines.CoroutineName\$Key",
                "--initialize-at-build-time=kotlinx.io.Buffer",
                "--initialize-at-build-time=org.xml.sax.helpers.LocatorImpl",
                "--initialize-at-build-time=kotlinx.io.Segment",
                "--initialize-at-build-time=kotlinx.io.bytestring.ByteString",
                "--initialize-at-build-time=kotlinx.io.bytestring.ByteString\$Companion",
                "--initialize-at-build-time=org.slf4j.helpers.SubstituteServiceProvider",
                "--initialize-at-build-time=org.slf4j.helpers.SubstituteLoggerFactory",
                "--initialize-at-build-time=org.slf4j.helpers.SubstituteLogger",
                "--initialize-at-build-time=org.slf4j.helpers.NOP_FallbackServiceProvider",
                "--initialize-at-build-time=org.slf4j.helpers.NOPLoggerFactory",
                "--initialize-at-build-time=org.slf4j.LoggerFactory",
                "--initialize-at-build-time=org.xml.sax.helpers.AttributesImpl",
                "-H:+InstallExitHandlers",
                "-H:+ReportUnsupportedElementsAtRuntime",
                "-H:+ReportExceptionStackTraces",
                "-H:IncludeResources=application.yml",
                "-H:IncludeResources=application-development.yml",
                "-H:ReflectionConfigurationFiles=$resourcePath/reflect-config.json",
                "-H:ResourceConfigurationFiles=$resourcePath/resource-config.json",
            )
        }

        // See https://graalvm.github.io/native-build-tools/0.10.4/gradle-plugin.html#testing-support
        named("test") {
            fallback.set(false)
            verbose.set(true)
            imageName.set("graalvm-test-server")

            // Support adding additional build arguments as CLI argument -PnativeBuildArgs
            val additionalArgs: List<String> = project.findProperty("nativeBuildArgs")?.toString()?.split(",") ?: emptyList()
            buildArgs.addAll(additionalArgs)
            buildArgs.addAll(
                "--initialize-at-build-time=ch.qos.logback",
                "--initialize-at-build-time=io.ktor,kotlin",
                "--initialize-at-build-time=kotlinx.coroutines.CoroutineName",
                "--initialize-at-build-time=kotlinx.coroutines.CoroutineName\$Key",
                "--initialize-at-build-time=kotlinx.io.Buffer",
                "--initialize-at-build-time=org.xml.sax.helpers.LocatorImpl",
                "--initialize-at-build-time=kotlinx.io.Segment",
                "--initialize-at-build-time=kotlinx.io.bytestring.ByteString",
                "--initialize-at-build-time=kotlinx.io.bytestring.ByteString\$Companion",
                "--initialize-at-build-time=org.slf4j.helpers.SubstituteServiceProvider",
                "--initialize-at-build-time=org.slf4j.helpers.SubstituteLoggerFactory",
                "--initialize-at-build-time=org.slf4j.helpers.SubstituteLogger",
                "--initialize-at-build-time=org.slf4j.helpers.NOP_FallbackServiceProvider",
                "--initialize-at-build-time=org.slf4j.helpers.NOPLoggerFactory",
                "--initialize-at-build-time=org.slf4j.LoggerFactory",
                "--initialize-at-build-time=org.xml.sax.helpers.AttributesImpl",
                "-H:+InstallExitHandlers",
                "-H:+ReportUnsupportedElementsAtRuntime",
                "-H:+ReportExceptionStackTraces",
                "-H:ReflectionConfigurationFiles=$resourcePath/reflect-config.json",
                "-H:ResourceConfigurationFiles=$resourcePath/resource-config.json",
            )
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

tasks.withType<DependencyUpdatesTask> {
    // ignore release candidates when checking for new gradle version
    gradleReleaseChannel = "current"

    // ignore release candidates as upgradable versions from stable versions for all other gradle dependencies
    rejectVersionIf {
        isNonStable(candidate.version) && !isNonStable(currentVersion)
    }
}
