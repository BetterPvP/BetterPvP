#!/usr/bin/env bash
# Installs the BetterPvP proxy plugin and its config into the proxy folder, then starts Velocity.
set -euo pipefail
dir=${BPVP_PROXY_DIR:-/server}
plugin=$dir/plugins/betterpvp-proxy
mkdir -p "$plugin"
cp /opt/bpvp/betterpvp-proxy.jar "$dir/plugins/betterpvp-proxy.jar"

# A mounted config.toml wins. Otherwise the network settings come from the environment.
if [[ -f ${BPVP_PROXY_CONFIG:-/config/betterpvp-proxy.toml} ]]; then
  cp "${BPVP_PROXY_CONFIG:-/config/betterpvp-proxy.toml}" "$plugin/config.toml"
else
  cat > "$plugin/config.toml" <<TOML
orchestration_base_url = "${BPVP_ORCHESTRATION_URL:-http://orchestration-service:8085/}"
orchestration_request_timeout_ms = 3000

[resourcepack]
enabled = false

[network]
enabled = ${BPVP_NETWORK:-true}
nats_url = "${BPVP_NATS_URL:-nats://nats:4222}"
nats_token = "${BPVP_NATS_TOKEN:-}"
prefix = "${BPVP_NATS_PREFIX:-bpvp}"
proxy_name = ""
hub_site = "${BPVP_HUB_SITE:-hub}"
TOML
fi

# Behind a load balancer that hides the player's address, Mojang rejects every join unless this is off.
if [[ -n ${BPVP_PREVENT_CLIENT_PROXY_CONNECTIONS:-} && -f $dir/velocity.toml ]]; then
  sed -i -E "s/^prevent-client-proxy-connections = .*/prevent-client-proxy-connections = $BPVP_PREVENT_CLIENT_PROXY_CONNECTIONS/" "$dir/velocity.toml"
fi

exec /__cacert_entrypoint.sh /usr/bin/run-bungeecord.sh "$@"
