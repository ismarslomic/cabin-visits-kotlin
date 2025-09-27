# syntax=docker/dockerfile:1
# Multi-arch Dockerfile that builds the GraalVM native image per-target platform

# --- Builder stage: build native image with GraalVM ---
FROM ghcr.io/graalvm/native-image-community:24-ol9 AS builder

WORKDIR /workspace

# Leverage Docker layer caching for dependencies
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
RUN chmod +x gradlew \
    && ./gradlew --no-daemon --version

# Copy sources
COPY src src
COPY META-INF META-INF
COPY README.md README.md

# Build the native image (glibc-based to avoid musl toolchain issues)
# You can tweak nativeBuildArgs via the Gradle property if needed
RUN ./gradlew nativeCompile --no-daemon

# --- Runtime stage: minimal distroless with glibc ---
FROM gcr.io/distroless/cc-debian12:nonroot

WORKDIR /
COPY --from=builder /workspace/build/native/nativeCompile/graalvm-server /graalvm-server

EXPOSE 8079
USER nonroot
ENTRYPOINT ["/graalvm-server"]
