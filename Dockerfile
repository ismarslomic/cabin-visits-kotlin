# This image is based on Oracle GraalVM Native Image, which is licensed under
# the Oracle GraalVM Free Terms and Conditions (GFTC).
# See: https://www.oracle.com/downloads/licenses/graalvm-free-license.html

# Stage 1: Build GraalVM Native Image (note! native image is platform specific)
FROM container-registry.oracle.com/graalvm/native-image:23.0.1 AS builder

# Install xargs and other utilities required by the gradle build process
RUN microdnf install -y findutils

# Set working directory
WORKDIR /app

# Copy the application code and Gradle wrapper
COPY . .

# Build the native image using the Gradle plugin
RUN ./gradlew nativeCompile --no-daemon

# Stage 2: Create a minimal container for the native image
FROM ubuntu:24.04 AS runtime

# Set working directory
WORKDIR /app

# Copy the native executable from the builder stage
COPY --from=builder /app/build/native/nativeCompile/graalvm-server .

# Ensure the binary has execute permissions
RUN chmod +x /app/graalvm-server

# Set the command to run the native executable
CMD ["/app/graalvm-server"]
