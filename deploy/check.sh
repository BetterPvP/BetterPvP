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

# The proxies turn everyone away while there is no hub, so the rest needs one.
[[ " ${ROLES:-hub} " == *" hub "* ]] || exit 0

# A server list ping through the load balancer, which only a proxy answers.
py=$(command -v python3 || command -v python) || { echo "python is needed for the ping" >&2; exit 1; }
status=$("$py" "$(dirname "$0")/ping.py" 127.0.0.1 25565 2>&1 || true)
if [[ $status != *'"name"'* ]]; then
  echo "FAIL: localhost:25565 did not answer a server list ping: $status" >&2
  exit 1
fi
echo "ok: localhost:25565 answers through a proxy: $status"

# Every proxy has added a hub to its server directory, so joining players land on one.
for pod in $(kubectl -n "$NS" get pods -l app.kubernetes.io/component=proxy -o name); do
  if ! kubectl -n "$NS" logs "$pod" -c proxy 2>/dev/null | grep -q "Added server 'hub-"; then
    echo "FAIL: $pod has not added a hub server" >&2
    exit 1
  fi
done
echo "ok: every proxy has a hub server to send players to"
