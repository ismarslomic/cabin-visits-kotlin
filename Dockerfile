# This Dockerfile is meant to be used only in the Github Workflow
FROM gcr.io/distroless/base:nonroot

COPY graalvm-server /graalvm-server

EXPOSE 8079

ENTRYPOINT ["/graalvm-server"]
