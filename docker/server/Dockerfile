# The one server image every role runs. Build it from the context stage.sh makes:
#   docker/server/stage.sh build/image && docker build -t ghcr.io/betterpvp/server:dev build/image
FROM eclipse-temurin:25-jre-noble

ARG RESTIC_VERSION=0.19.1
ARG RESTIC_SHA256=f415415624dcc452f2a02b8c33641791a8c6d6d3b65bbb3543fcf9a25151585c
ARG YQ_VERSION=v4.54.3
ARG YQ_SHA256=129534601871538efe0ba4c5e62a34fb1d5f469bcb975c4fddeb467d82974973

RUN apt-get update \
 && apt-get install -y --no-install-recommends bsdextrautils bzip2 ca-certificates curl jq \
 && rm -rf /var/lib/apt/lists/* \
 && curl -fsSL -o /tmp/restic.bz2 "https://github.com/restic/restic/releases/download/v${RESTIC_VERSION}/restic_${RESTIC_VERSION}_linux_amd64.bz2" \
 && echo "${RESTIC_SHA256}  /tmp/restic.bz2" | sha256sum -c - \
 && bunzip2 -c /tmp/restic.bz2 > /usr/local/bin/restic && chmod 755 /usr/local/bin/restic && rm /tmp/restic.bz2 \
 && curl -fsSL -o /usr/local/bin/yq "https://github.com/mikefarah/yq/releases/download/${YQ_VERSION}/yq_linux_amd64" \
 && echo "${YQ_SHA256}  /usr/local/bin/yq" | sha256sum -c - \
 && chmod 755 /usr/local/bin/yq

COPY bpvp/ /opt/bpvp/
COPY entrypoint.sh /usr/local/bin/bpvp-server
RUN chmod 755 /usr/local/bin/bpvp-server && mkdir -p /data && chown 1000:1000 /data

ARG REVISION=dev
ENV BPVP_ROLE=all \
    BPVP_REVISION=${REVISION}
LABEL org.opencontainers.image.source="https://github.com/BetterPvP/BetterPvP" \
      org.opencontainers.image.revision="${REVISION}"

USER 1000
WORKDIR /data
EXPOSE 25565
ENTRYPOINT ["/usr/local/bin/bpvp-server"]
