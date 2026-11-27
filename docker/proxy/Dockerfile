# The Velocity proxy with the BetterPvP proxy plugin, for Shulker's ProxyFleet:
#   ./gradlew :proxy:shadowJar && cp build/proxy.jar docker/proxy/ && docker build -t ghcr.io/betterpvp/proxy:local docker/proxy
FROM itzg/mc-proxy:2026.11.2-java25
ARG REVISION=dev
COPY proxy.jar /opt/bpvp/betterpvp-proxy.jar
COPY start.sh /usr/local/bin/bpvp-proxy
ENV BPVP_REVISION=${REVISION}
LABEL org.opencontainers.image.source="https://github.com/BetterPvP/BetterPvP" \
      org.opencontainers.image.revision="${REVISION}"
ENTRYPOINT ["/usr/local/bin/bpvp-proxy"]
