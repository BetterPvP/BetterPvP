#!/usr/bin/env bash
# Checks a server image against the local docker-compose stack.
#   docker/server/check.sh <image>
# Starts it as role all and waits for Paper's "Done", then checks a camp server drops MythicMobs and Dungeons.
set -euo pipefail
image=${1:?usage: check.sh <image>}
root=$(cd "$(dirname "$0")/../.." && pwd)
compose=(docker compose -f "$root/docker/docker-compose.yml")
network=betterpvp_default
name=bpvp-image-check

db=bpvp_check_$RANDOM
psql() { docker exec postgres psql -q -U user -d betterpvp -c "$1"; }
cleanup() {
  docker rm -f "$name" "$name-camp" >/dev/null 2>&1 || true
  psql "DROP DATABASE IF EXISTS $db" >/dev/null 2>&1 || true
}
trap cleanup EXIT
cleanup

"${compose[@]}" up -d --wait
# A database of its own, so the check never migrates or reads the dev database.
psql "CREATE DATABASE $db"
nats=()
if "${compose[@]}" config --services | grep -qx nats; then
  nats=(-e "BPVP_CONFIG_SET=plugins/Core/config.yml .core.nats.enabled true
plugins/Core/config.yml .core.nats.url nats://nats:4222")
fi

docker run -d --name "$name" --network "$network" -e BPVP_ROLE=all -e BPVP_SERVER_NAME=check \
  -e BPVP_DB_HOST=postgres:5432 -e "BPVP_DB_NAME=$db" -e BPVP_DB_USER=user -e 'BPVP_DB_PASSWORD=BetterPvP123!' \
  "${nats[@]}" "$image" >/dev/null

for _ in $(seq 120); do
  if docker logs "$name" 2>&1 | grep -q 'Done ('; then
    echo "ok: role all reached Done with $(docker logs "$name" 2>&1 | grep -c ' ERROR]:' || true) startup errors"
    break
  fi
  if [[ $(docker inspect -f '{{.State.Running}}' "$name") != true ]]; then
    docker logs --tail 60 "$name"
    echo "FAIL: role all exited before Done" >&2
    exit 1
  fi
  sleep 5
done
docker logs "$name" 2>&1 | grep -q 'Done (' || { docker logs --tail 60 "$name"; echo "FAIL: no Done after 10 minutes" >&2; exit 1; }
if docker logs "$name" 2>&1 | grep -Eq 'Migration of schema .* failed|configure the Postgres database'; then
  echo "FAIL: Core could not set up its database" >&2
  exit 1
fi

docker run -d --name "$name-camp" -e BPVP_ROLE=camp "$image" >/dev/null
for _ in $(seq 30); do
  docker logs "$name-camp" 2>&1 | grep -q '^Starting camp' && break
  sleep 1
done
docker exec "$name-camp" ls plugins > /tmp/bpvp-camp-plugins.txt
if grep -Eiq '^(mythicmobs.*|dungeons)\.jar$' /tmp/bpvp-camp-plugins.txt; then
  cat /tmp/bpvp-camp-plugins.txt
  echo "FAIL: a camp server still has MythicMobs or Dungeons" >&2
  exit 1
fi
grep -qx core.jar /tmp/bpvp-camp-plugins.txt || { cat /tmp/bpvp-camp-plugins.txt; echo "FAIL: camp has no core.jar" >&2; exit 1; }
echo "ok: role camp has no MythicMobs or Dungeons jar"
