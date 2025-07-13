# Documentation for developers of this repo

### Install GraalVM (for Mac)

You can follow [official installation](https://www.graalvm.org/latest/docs/getting-started/macos/) steps
or by using [sdkman](https://sdkman.io) bellow, which can be installed
with [homebrew](https://github.com/sdkman/homebrew-tap).

```bash
sdk env install
sdk env
```

To check whether the installation was successful, run

```bash
$ java -version
java version "23.0.2" 2025-01-21
Java(TM) SE Runtime Environment Oracle GraalVM 23.0.2+7.1 (build 23.0.2+7-jvmci-b01)
Java HotSpot(TM) 64-Bit Server VM Oracle GraalVM 23.0.2+7.1 (build 23.0.2+7-jvmci-b01, mixed mode, sharing)
```

### Run tests

```bash
./gradlew test
```

### Collect metadata with the Tracing Agent

GraalVM provides a Tracing Agent to easily gather metadata and prepare configuration files. The agent tracks all usages
of dynamic features during application execution on a regular Java VM.
Read more
at [Collect Metadata with the Tracing Agent](https://www.graalvm.org/latest/reference-manual/native-image/metadata/AutomaticMetadataCollection/)

```bash
# Build
./gradlew clean build

# Source all env variables from the .env file
set -a; source .env; set +a

# Run tracing agent
java -agentlib:native-image-agent=config-output-dir=META-INF/native-image/main -jar ./build/libs/cabin-visits-kotlin-all.jar
```

The collected metadata will be written to
the [META-INF/native-image/main/reachability-metadata.json](META-INF/native-image/main/reachability-metadata.json).

### Build GraalVM native image

Build the GraalVm native image as an executable file, with use
of [Gradle plugin for GraalVM Native Image building](https://graalvm.github.io/native-build-tools/0.10.4/gradle-plugin.html).

Optional argument `-PnativeBuildArgs=""` can be used to add additional build arguments to the native image compilation.
Use comma to separate arguments.

```bash
./gradlew nativeCompile
```

The native image will be compiled to `./build/native/nativeCompile/graalvm-server`.

### Build GraalVM native image for tests

Build the GraalVm native image as an executable file for running tests as native image.
This means that tests will be compiled and executed as native code.
Read more [here](https://graalvm.github.io/native-build-tools/0.10.4/gradle-plugin.html#testing-support).

Optional argument `-PnativeBuildArgs=""` can be used to add additional build arguments to the native image
compilation. Use comma to separate arguments.

The native image will be compiled to `./build/native/nativeTestCompile/graalvm-test-server`.

```bash
./gradlew nativeTestCompile
```

### Check for available dependency updates

Overview of dependencies with version updates available:

```bash
./gradlew dependencyUpdates
```

### Kotlin linter

Perform Kotlin linter check

```bash
./gradlew lintKotlin     
```

Automatically correct linting warnings

```bash
./gradlew formatKotlin
```

### Static code analysis with Detekt

Run a detekt analysis and complexity report the source files with use
of [Detekt Gradle plugin](https://detekt.dev/docs/gettingstarted/gradle/).

Reports are generated in the folder `./build/reports/detekt`.

```bash
./gradlew detekt
```
