#!/usr/bin/env bash
# Brings up the local network on k3d: Agones, Shulker, NATS, Postgres, two Velocity proxies and a fleet per role.
# Safe to run again: it upgrades what is there. See deploy/README.md for the variables.
set -euo pipefail
cd "$(dirname "$0")"

CLUSTER=${CLUSTER:-bpvp}
NS=bpvp
IMAGE=${BPVP_IMAGE:-ghcr.io/betterpvp/server:camps-main}
SERVER_MEMORY=${SERVER_MEMORY:-3Gi}
ROLES=${ROLES:-hub resource camp dungeon event}
K3S_IMAGE=rancher/k3s:v1.34.13-k3s1
AGONES_VERSION=1.62.0
SHULKER_VERSION=v0.13.0
NATS_VERSION=2.15.3
CNPG_VERSION=0.29.2
CACHE=${XDG_CACHE_HOME:-$HOME/.cache}/bpvp-deploy

for tool in docker k3d kubectl helm git; do
  command -v "$tool" >/dev/null || { echo "$tool is not installed" >&2; exit 1; }
done

step() { echo; echo "== $*"; }

step "Cluster $CLUSTER"
if ! k3d cluster list "$CLUSTER" >/dev/null 2>&1; then
  k3d cluster create "$CLUSTER" --image "$K3S_IMAGE" -p "25565:25565@loadbalancer" \
    --k3s-arg "--disable=traefik@server:0" --wait
fi
kubectl config use-context "k3d-$CLUSTER" >/dev/null
kubectl create namespace "$NS" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
kubectl create namespace shulker-system --dry-run=client -o yaml | kubectl apply -f - >/dev/null

step "Image $IMAGE"
if [[ ${BPVP_IMAGE_LOCAL:-} == 1 ]]; then
  k3d image import -c "$CLUSTER" "$IMAGE"
  pull_policy=Never
else
  pull_policy=IfNotPresent
fi

step "Secrets"
if [[ -n ${GHCR_TOKEN:-} ]]; then
  kubectl -n "$NS" create secret docker-registry ghcr --docker-server=ghcr.io \
    --docker-username="${GHCR_USER:?GHCR_USER is needed with GHCR_TOKEN}" --docker-password="$GHCR_TOKEN" \
    --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  echo "ghcr pull secret set"
elif [[ $pull_policy != Never ]]; then
  echo "GHCR_TOKEN is not set, so the cluster can only pull a public image" >&2
fi
if [[ -n ${ASSETS_BUCKET:-} ]]; then
  kubectl -n "$NS" create secret generic assets --from-literal=bucket="$ASSETS_BUCKET" \
    --from-literal=access-key-id="${ASSETS_AWS_ACCESS_KEY_ID:?}" \
    --from-literal=secret-access-key="${ASSETS_AWS_SECRET_ACCESS_KEY:?}" \
    --from-literal=restic-password="${ASSETS_RESTIC_PASSWORD:?}" \
    --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  echo "assets secret set"
fi

step "Agones $AGONES_VERSION"
helm repo add agones https://agones.dev/chart/stable --force-update >/dev/null
helm repo add nats https://nats-io.github.io/k8s/helm/charts/ --force-update >/dev/null
helm repo add cnpg https://cloudnative-pg.github.io/charts --force-update >/dev/null
helm upgrade --install agones agones/agones --version "$AGONES_VERSION" -n agones-system --create-namespace \
  -f values/agones.yaml --wait --timeout 10m

step "CloudNativePG $CNPG_VERSION"
helm upgrade --install cnpg cnpg/cloudnative-pg --version "$CNPG_VERSION" -n cnpg-system --create-namespace \
  -f values/cnpg.yaml --wait --timeout 10m

step "NATS $NATS_VERSION"
helm upgrade --install nats nats/nats --version "$NATS_VERSION" -n "$NS" -f values/nats.yaml --wait --timeout 10m

step "Shulker $SHULKER_VERSION"
shulker=$CACHE/shulker-$SHULKER_VERSION
if [[ ! -d $shulker ]]; then
  mkdir -p "$CACHE"
  git clone -q --depth 1 -b "$SHULKER_VERSION" https://github.com/jeremylvln/Shulker.git "$shulker"
fi
helm upgrade --install shulker "$shulker/kube/helm" -n shulker-system -f values/shulker.yaml --wait --timeout 10m

step "Postgres"
kubectl apply -f manifests/postgres.yaml
kubectl -n "$NS" wait --for=condition=Ready cluster/postgres --timeout=10m

step "Proxies and servers"
kubectl apply -f manifests/network.yaml
sed -e "s|__IMAGE__|$IMAGE|" -e "s|__PULL_POLICY__|$pull_policy|" -e "s|__SERVER_MEMORY__|$SERVER_MEMORY|" \
  manifests/servers.yaml | kubectl apply -f -

# Shulker keeps each <role>-template Fleet at no replicas, because it resets replicas whenever it reconciles. The role's
# own Fleet copies that template and adds the counters core reports, loose health and a stop grace.
for role in $ROLES; do
  f=$(jq -c --arg r "$role" '.[$r] // empty' manifests/fleets.json)
  [[ -n $f ]] || { echo "unknown role $role in ROLES" >&2; exit 1; }
  for _ in $(seq 60); do kubectl -n "$NS" get fleet "$role-template" >/dev/null 2>&1 && break; sleep 2; done
  kubectl -n "$NS" get fleet "$role-template" -o json | jq --arg role "$role" --argjson f "$f" '{
    apiVersion, kind,
    metadata: {name: $role, namespace: .metadata.namespace, labels: (.metadata.labels + {"betterpvp.net/role": $role})},
    spec: (.spec
      | .replicas = $f.replicas
      | .template.metadata.labels["betterpvp.net/role"] = $role
      | .template.spec.counters = ($f.counters | map_values({count: 0, capacity: .}))
      | .template.spec.health = {disabled: false, initialDelaySeconds: 180, periodSeconds: 15, failureThreshold: 12}
      | .template.spec.template.spec.terminationGracePeriodSeconds = 45)
  }' | kubectl apply -f -
  if jq -e '.buffer' <<<"$f" >/dev/null; then
    jq -n --arg role "$role" --arg ns "$NS" --argjson f "$f" '{
      apiVersion: "autoscaling.agones.dev/v1", kind: "FleetAutoscaler",
      metadata: {name: $role, namespace: $ns},
      spec: {fleetName: $role, policy: {type: "Buffer", buffer: $f.buffer}}
    }' | kubectl apply -f -
  fi
done
# Roles left out of ROLES stop.
for role in $(jq -j 'keys | join(" ")' manifests/fleets.json); do
  [[ " $ROLES " == *" $role "* ]] && continue
  kubectl -n "$NS" delete fleetautoscaler "$role" --ignore-not-found
  kubectl -n "$NS" delete fleet "$role" --ignore-not-found
done

step "Status"
kubectl -n "$NS" get fleets
echo
echo "Servers take a few minutes to start. deploy/check.sh waits for every fleet to be Ready."
echo "Join at localhost:25565."
