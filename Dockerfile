# This Dockerfile is meant to be used only in the Github Workflow

# Use a lightweight image for setting permissions to the GraalVM native image (not supported in distroless)
FROM alpine:latest AS builder
COPY graalvm-server /graalvm-server
RUN chmod +x /graalvm-server

# Use the distroless image for the final container
FROM gcr.io/distroless/base:nonroot
COPY --from=builder /graalvm-server /graalvm-server
ENTRYPOINT ["/graalvm-server"]
