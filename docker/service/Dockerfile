# One image per standalone service, built from its Gradle installDist output:
#   ./gradlew :clans-service:installDist
#   docker build -f docker/service/Dockerfile --build-arg SERVICE=clans-service clans-service/build/install/clans-service
FROM eclipse-temurin:25-jre-noble
ARG SERVICE
ARG REVISION=dev
COPY . /opt/service/
RUN ln -s "/opt/service/bin/${SERVICE}" /usr/local/bin/service
ENV BPVP_REVISION=${REVISION} \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
LABEL org.opencontainers.image.source="https://github.com/BetterPvP/BetterPvP" \
      org.opencontainers.image.revision="${REVISION}"
USER 1000
WORKDIR /opt/service
ENTRYPOINT ["/usr/local/bin/service"]
