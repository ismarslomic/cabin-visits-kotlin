# cabin-visits-kotlin

Kotlin app using Ktor server application with GraalVM to create HTTP endpoints to receive data from different sources to
collect all data belonging to a visit at Slomic Smarthytte and store it to a database.

## Licensing Information

This project uses Oracle GraalVM Native Image, which is subject to
the [Oracle GraalVM Free Terms and Conditions (GFTC)](https://www.oracle.com/downloads/licenses/graal-free-license.html).

### Install GraalVM (for Mac)

You can follow [official installation](https://www.graalvm.org/latest/docs/getting-started/macos/) steps or by using
Homebrew and [jenv](https://www.jenv.be) bellow.

```bash
brew install --cask graalvm-jdk
jenv add 23.0.1-graal /Library/Java/JavaVirtualMachines/graalvm-23.jdk/Contents/Home/
```

To check whether the installation was successful, run

```bash
$ java -version
java version "23.0.1" 2024-10-15
Java(TM) SE Runtime Environment Oracle GraalVM 23.0.1+11.1 (build 23.0.1+11-jvmci-b01)
Java HotSpot(TM) 64-Bit Server VM Oracle GraalVM 23.0.1+11.1 (build 23.0.1+11-jvmci-b01, mixed mode, sharing)
```

Note! jenv will report a deprecation warning, but you can ignore it.

## Building & Running

To build or run the project, use one of the following tasks:

| Task                                                   | Description                                                 |
|--------------------------------------------------------|-------------------------------------------------------------|
| `./gradlew test`                                       | Run the tests                                               |
| `./gradlew nativeCompile`                              | Build the GraalVm native image as executable file           |
| `./build/native/nativeCompile/graalvm-server`          | Run the executable file                                     |
| `./gradlew nativeTestCompile`                          | Build the GraalVm native image as executable file for tests |
| `./build/native/nativeTestCompile/graalvm-test-server` | Run the tests in native image                               |

## HTTP API

```bash
curl http://localhost:8079
```

## Metrics

```bash
curl http://localhost:8079/metrics
```
