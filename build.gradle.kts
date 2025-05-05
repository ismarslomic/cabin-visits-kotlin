import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

plugins {
    application
    alias(libs.plugins.jacoco)
    alias(libs.plugins.detekt)
    alias(libs.plugins.graalvm)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.ktor)
    alias(libs.plugins.versions)
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
    implementation(libs.bundles.database)
    implementation(libs.bundles.google.calendar)
    implementation(libs.bundles.ktor)
    implementation(libs.bundles.metrics)
    implementation(libs.hoplite.yaml)
    implementation(libs.influxdb.client)
    implementation(libs.logback.classic)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.ktor.server.test)
    testImplementation(libs.mockk)
}

// See https://graalvm.github.io/native-build-tools/0.10.4/gradle-plugin.html
graalvmNative {
    val mainConfigurationFilesPath = "$projectDir/META-INF/native-image/main"
    val testConfigurationFilesPath = "$projectDir/META-INF/native-image/test"

    binaries {

        named("main") {
            fallback = false // Sets the fallback mode of native-image, defaults to false
            verbose = true // Add verbose output, defaults to false
            imageName.set("graalvm-server")

            // Support adding additional build arguments as CLI argument -PnativeBuildArgs
            val additionalArgs: List<String> =
                project.findProperty("nativeBuildArgs")?.toString()?.split(",") ?: emptyList()
            buildArgs.addAll(additionalArgs)
            buildArgs.addAll(
                "--initialize-at-build-time=ch.qos.logback",
                "--initialize-at-build-time=io.ktor,kotlin",
                "--initialize-at-build-time=kotlinx.coroutines.CoroutineName",
                "--initialize-at-build-time=kotlinx.coroutines.CoroutineName\$Key",
                "--initialize-at-build-time=kotlinx.io.Buffer",
                "--initialize-at-build-time=kotlinx.io.Segment",
                "--initialize-at-build-time=kotlinx.io.bytestring.ByteString",
                "--initialize-at-build-time=kotlinx.io.bytestring.ByteString\$Companion",
                "--initialize-at-build-time=org.slf4j.LoggerFactory",
                "--initialize-at-build-time=org.slf4j.helpers.NOPLoggerFactory",
                "--initialize-at-build-time=org.slf4j.helpers.NOP_FallbackServiceProvider",
                "--initialize-at-build-time=org.slf4j.helpers.NOPMDCAdapter",
                "--initialize-at-build-time=org.slf4j.helpers.BasicMDCAdapter",
                "--initialize-at-build-time=org.slf4j.helpers.BasicMDCAdapter$1",
                "--initialize-at-build-time=org.slf4j.helpers.SubstituteLogger",
                "--initialize-at-build-time=org.slf4j.helpers.SubstituteLoggerFactory",
                "--initialize-at-build-time=org.slf4j.helpers.SubstituteServiceProvider",
                "--initialize-at-build-time=org.xml.sax.helpers.AttributesImpl",
                "--initialize-at-build-time=org.xml.sax.helpers.LocatorImpl",
                "-H:+InstallExitHandlers",
                "-H:+ReportUnsupportedElementsAtRuntime",
                "-H:+ReportExceptionStackTraces",
                "-H:IncludeResources=application.yml",
                "-H:IncludeResources=application-development.yml",
                "-H:ConfigurationFileDirectories=$mainConfigurationFilesPath",
            )
        }

        // See https://graalvm.github.io/native-build-tools/0.10.4/gradle-plugin.html#testing-support
        named("test") {
            fallback.set(false)
            verbose.set(true)
            imageName.set("graalvm-test-server")

            // Support adding additional build arguments as CLI argument -PnativeBuildArgs
            val additionalArgs: List<String> =
                project.findProperty("nativeBuildArgs")?.toString()?.split(",") ?: emptyList()
            buildArgs.addAll(additionalArgs)
            buildArgs.addAll(
                "--initialize-at-build-time=ch.qos.logback",
                "--initialize-at-build-time=io.kotest",
                "--initialize-at-build-time=io.ktor,kotlin",
                "--initialize-at-build-time=kotlinx.coroutines.CoroutineDispatcher\$Key",
                "--initialize-at-build-time=kotlinx.coroutines.CoroutineName",
                "--initialize-at-build-time=kotlinx.coroutines.CoroutineName\$Key",
                "--initialize-at-build-time=kotlinx.coroutines.ExecutorCoroutineDispatcherImpl",
                "--initialize-at-build-time=kotlinx.coroutines.internal.Symbol",
                "--initialize-at-build-time=kotlinx.coroutines.sync.MutexImpl",
                "--initialize-at-build-time=kotlinx.coroutines.sync.SemaphoreSegment",
                "--initialize-at-build-time=kotlinx.io.Buffer",
                "--initialize-at-build-time=kotlinx.io.Segment",
                "--initialize-at-build-time=kotlinx.io.bytestring.ByteString",
                "--initialize-at-build-time=kotlinx.io.bytestring.ByteString\$Companion",
                "--initialize-at-build-time=org.slf4j.LoggerFactory",
                "--initialize-at-build-time=org.slf4j.helpers.NOPLoggerFactory",
                "--initialize-at-build-time=org.slf4j.helpers.NOP_FallbackServiceProvider",
                "--initialize-at-build-time=org.slf4j.helpers.NOPMDCAdapter",
                "--initialize-at-build-time=org.slf4j.helpers.BasicMDCAdapter",
                "--initialize-at-build-time=org.slf4j.helpers.BasicMDCAdapter$1",
                "--initialize-at-build-time=org.slf4j.helpers.SubstituteLogger",
                "--initialize-at-build-time=org.slf4j.helpers.SubstituteLoggerFactory",
                "--initialize-at-build-time=org.slf4j.helpers.SubstituteServiceProvider",
                "--initialize-at-build-time=org.xml.sax.helpers.AttributesImpl",
                "--initialize-at-build-time=org.xml.sax.helpers.LocatorImpl",
                "-H:+InstallExitHandlers",
                "-H:+ReportUnsupportedElementsAtRuntime",
                "-H:+ReportExceptionStackTraces",
                "-H:ConfigurationFileDirectories=$testConfigurationFilesPath/",
            )
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
        /*
           - To use withEnvironment (kotest) with JDK17+ we need to add arguments for the JVM that runs the tests
         */
        jvmArgs(
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
        )
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(true)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    /*
       - To use withEnvironment (kotest) with JDK17+ we need to add arguments for the JVM that runs the tests
     */
    jvmArgs(
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        // Run the Native Image Agent as part of the test task in Gradle to collect the metadata for native test image,
        // However, there is a known issue with the JvmMockKGateway and bytebuddy that is currently unresolved,
        // see https://github.com/mockk/mockk/issues/1022
        // "-agentlib:native-image-agent=config-merge-dir=META-INF/native-image/test/",
    )
    finalizedBy(tasks.jacocoTestReport)
}

// Detekt
tasks.withType<Detekt>().configureEach {
    jvmTarget = "21" // Currently max supported java version in Detekt
}
tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = "21" // Currently max supported java version in Detekt
}

tasks.withType<Detekt>().configureEach {
    reports {
        html.required.set(true) // observe findings in your browser with structure and code snippets
        xml.required.set(true) // checkstyle like format mainly for integrations like Jenkins
        sarif.required.set(true) // standardized SARIF format (https://sarifweb.azurewebsites.net/) to support integrations with GitHub Code Scanning
        md.required.set(true) // simple Markdown format
    }
}

// Kotlinter - install the hook automatically when someone runs the build
tasks.check {
    dependsOn("installKotlinterPrePushHook")
}

// Activate locking for all configurations
// See https://docs.gradle.org/current/userguide/dependency_locking.html
dependencyLocking {
    lockAllConfigurations()
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
