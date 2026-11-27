#!/usr/bin/env bash
# Removes the local cluster and everything in it.
set -euo pipefail
CLUSTER=${CLUSTER:-bpvp}
if k3d cluster list "$CLUSTER" >/dev/null 2>&1; then
  k3d cluster delete "$CLUSTER"
else
  echo "no cluster $CLUSTER"
fi
