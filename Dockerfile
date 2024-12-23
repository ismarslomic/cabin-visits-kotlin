# This Dockerfile is meant to be used only in the Github Workflow

FROM gcr.io/distroless/base:nonroot

COPY graalvm-server /graalvm-server

# The distroless/base:nonroot image runs as a non-root user by default, which means we need to ensure the binary
# is both executable and accessible to the non-root user.
RUN chmod +x /graalvm-server

ENTRYPOINT ["/graalvm-server"]
