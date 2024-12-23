# This Dockerfile is meant to be used only in the Github Actions

FROM gcr.io/distroless/base:nonroot

# Note! The GraalVM native images are plattform dependend. You can't build the native image on Mac OS and run it in the
# Linux image
COPY build/native/nativeCompile/graalvm-server /graalvm-server

ENTRYPOINT ["/graalvm-server"]
