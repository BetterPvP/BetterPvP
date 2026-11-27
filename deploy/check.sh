#!/usr/bin/env bash
# Checks the local cluster.
#   deploy/check.sh        every fleet reaches Ready and localhost:25565 answers through a proxy
#   deploy/check.sh down   nothing of the cluster is left after deploy/down.sh
set -euo pipefail
CLUSTER=${CLUSTER:-bpvp}
NS=bpvp
TIMEOUT=${TIMEOUT:-900}

if [[ ${1:-} == down ]]; then
  left=$(docker ps -a --format '{{.Names}}' | grep -c "^k3d-$CLUSTER-" || true)
  volumes=$(docker volume ls --format '{{.Name}}' | grep -c "^k3d-$CLUSTER-" || true)
  if k3d cluster list "$CLUSTER" >/dev/null 2>&1 || (( left + volumes > 0 )); then
    echo "FAIL: cluster $CLUSTER still has $left containers and $volumes volumes" >&2
    exit 1
  fi
  echo "ok: cluster $CLUSTER is gone"
  exit 0
fi

kubectl config use-context "k3d-$CLUSTER" >/dev/null

# Ready game servers each fleet needs: the proxies, then a server per role in ROLES. Event starts at none.
declare -A want=([proxy]=2 [hub]=1 [resource]=1 [camp]=1 [dungeon]=1 [event]=0)
deadline=$(( $(date +%s) + TIMEOUT ))
for fleet in proxy ${ROLES:-hub resource camp dungeon event}; do
  while true; do
    ready=$(kubectl -n "$NS" get fleet "$fleet" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || true)
    allocated=$(kubectl -n "$NS" get fleet "$fleet" -o jsonpath='{.status.allocatedReplicas}' 2>/dev/null || true)
    (( ${ready:-0} + ${allocated:-0} >= want[$fleet] )) && kubectl -n "$NS" get fleet "$fleet" >/dev/null 2>&1 && break
    if (( $(date +%s) > deadline )); then
      kubectl -n "$NS" get fleets,gameservers
      echo "FAIL: fleet $fleet has ${ready:-0} Ready of ${want[$fleet]}" >&2
      exit 1
    fi
    sleep 10
  done
  echo "ok: fleet $fleet has ${ready:-0} Ready and ${allocated:-0} Allocated"
done

# Every GameServer declares the counters core reports.
for gs in $(kubectl -n "$NS" get gameservers -l betterpvp.net/role -o name); do
  counters=$(kubectl -n "$NS" get "$gs" -o jsonpath='{.spec.counters}')
  if [[ $counters != *'"players"'* || $counters != *'"camp-slots"'* ]]; then
    echo "FAIL: $gs does not declare the players and camp-slots counters" >&2
    exit 1
  fi
done
echo "ok: every server declares the players and camp-slots counters"

# A server can be Ready with a BetterPvP plugin broken, so each server's log must show Core running.
for gs in $(kubectl -n "$NS" get gameservers -l betterpvp.net/role -o name); do
  log=$(kubectl -n "$NS" logs "${gs#*/}" -c minecraft-server 2>/dev/null || true)
  if grep -E "Error occurred while enabling (Core|Clans|Champions|Shops|Progression|Dungeons|Events|Store) " <<<"$log"; then
    echo "FAIL: a BetterPvP plugin did not start on ${gs#*/}" >&2
    exit 1
  fi
  grep -q "Reporting lifecycle to the Agones sidecar" <<<"$log" || { echo "FAIL: Core is not running on ${gs#*/}" >&2; exit 1; }
done
echo "ok: Core and every BetterPvP plugin started on every server"

# The services run, and Placement may read GameServers and allocate them.
for service in ${SERVICES:-orchestration-service clans-service ledger-service sessions-service}; do
  if ! kubectl -n "$NS" rollout status "deployment/$service" --timeout=300s >/dev/null; then
    kubectl -n "$NS" logs "deployment/$service" --tail 30 >&2 || true
    echo "FAIL: $service is not running" >&2
    exit 1
  fi
  echo "ok: $service is running"
done
placement=system:serviceaccount:$NS:orchestration-service
for check in "list gameservers.agones.dev" "create gameserverallocations.allocation.agones.dev"; do
  # shellcheck disable=SC2086
  [[ $(kubectl -n "$NS" auth can-i $check --as="$placement") == yes ]] || { echo "FAIL: Placement cannot $check" >&2; exit 1; }
done
echo "ok: Placement can list GameServers and allocate them"

# The proxies turn everyone away while there is no hub, so the rest needs one.
[[ " ${ROLES:-hub} " == *" hub "* ]] || exit 0

# Every proxy adds a Ready hub to its server directory a few seconds later, so joining players land on one.
for pod in $(kubectl -n "$NS" get pods -l app.kubernetes.io/component=proxy -o name); do
  for _ in $(seq 30); do
    kubectl -n "$NS" logs "$pod" -c proxy 2>/dev/null | grep -q "Added server 'hub-" && continue 2
    sleep 5
  done
  echo "FAIL: $pod has not added a hub server" >&2
  exit 1
done
echo "ok: every proxy has a hub server to send players to"

# The BetterPvP proxy plugin runs on every proxy and has joined the network.
for pod in $(kubectl -n "$NS" get pods -l app.kubernetes.io/component=proxy -o name); do
  if ! kubectl -n "$NS" logs "$pod" -c proxy 2>/dev/null | grep -q "joined the network"; then
    echo "FAIL: $pod has no BetterPvP proxy plugin on the network" >&2
    exit 1
  fi
done
echo "ok: every proxy runs the BetterPvP plugin on the network"

# The question a proxy asks Placement when a player joins. Placement allocates a hub and names it.
request='{"v":1,"topic":"placement.place","origin":"deploy-check","correlationId":"deploy-check","reply":false,"payload":{"site":"hub","owner":"0","owned":"false","asker":"deploy-check"}}'
answer=$(kubectl -n "$NS" run "placement-check-$RANDOM" --rm -i --restart=Never --quiet --image=natsio/nats-box:0.18.1 --command -- nats req -s nats://nats:4222 --timeout 10s bpvp.bus.placement.place "$request" 2>&1 | grep '^{' || true)
hub=$(jq -r '.payload.server // empty' <<<"$answer" 2>/dev/null | tr -d '\r')
if [[ -z $hub ]]; then
  echo "FAIL: Placement placed no hub: ${answer:-no answer}" >&2
  exit 1
fi
for pod in $(kubectl -n "$NS" get pods -l app.kubernetes.io/component=proxy -o name); do
  kubectl -n "$NS" logs "$pod" -c proxy | grep -q "Added server '$hub'" || { echo "FAIL: $pod does not know $hub" >&2; exit 1; }
done
echo "ok: Placement sends joining players to $hub, which every proxy knows"

# A server list ping through the load balancer, which only a proxy answers.
py=$(command -v python3 || command -v python) || { echo "python is needed for the ping" >&2; exit 1; }
for _ in $(seq 12); do
  status=$("$py" "$(dirname "$0")/ping.py" 127.0.0.1 25565 2>&1 || true)
  [[ $status == *'"name"'* ]] && break
  sleep 5
done
if [[ $status != *'"name"'* ]]; then
  echo "FAIL: localhost:25565 did not answer a server list ping: $status" >&2
  exit 1
fi
echo "ok: localhost:25565 answers through a proxy: $status"
