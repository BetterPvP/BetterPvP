#!/usr/bin/env bash
# Builds every image the cluster runs from this checkout, with no registry and no CI secrets.
#   PLUGIN_DIR=<folder of plugin jars> deploy/build-local.sh [tag]
# Images are named bpvp.local/<name>:<tag> (default tag: local), so they can never be pushed to the team's registry.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
tag=${1:-local}
services=${SERVICES:-orchestration-service clans-service ledger-service sessions-service}
work=${BUILD_DIR:-$root/build/local-images}

: "${PLUGIN_DIR:?PLUGIN_DIR must name a folder with the plugins.json jars that have no url, see deploy/README.md}"
if [[ ! -d private ]]; then
  echo "private/ is missing, so the image has no Dungeons, Events or Store and the dungeon and event roles have nothing to run" >&2
fi

tasks=(shadowJar)
for service in $services; do tasks+=(":$service:installDist"); done
./gradlew "${tasks[@]}" --console=plain -q

docker/server/stage.sh "$work/server" >/dev/null
docker build -q -t "bpvp.local/server:$tag" --build-arg REVISION="$(git rev-parse HEAD)" "$work/server"

mkdir -p "$work/proxy"
cp docker/proxy/Dockerfile docker/proxy/start.sh build/proxy.jar "$work/proxy/"
docker build -q -t "bpvp.local/proxy:$tag" "$work/proxy"

for service in $services; do
  docker build -q -f docker/service/Dockerfile --build-arg SERVICE="$service" -t "bpvp.local/$service:$tag" \
    "$service/build/install/$service"
done

docker images --format '{{.Repository}}:{{.Tag}}  {{.Size}}' | grep "^bpvp.local/.*:$tag "
