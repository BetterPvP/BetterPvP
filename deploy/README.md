# Local cluster

One command runs the whole network on your machine in a k3d cluster:

| Piece | Version |
|---|---|
| k3s | v1.34.13-k3s1 |
| Agones | 1.62.0 |
| Shulker | v0.13.0 |
| NATS | chart 2.15.3, 3 nodes with JetStream |
| CloudNativePG | chart 0.29.2, one Postgres instance |
| Velocity | 2 proxies with the BetterPvP proxy plugin, on `localhost:25565` |
| Paper | the server image, one fleet per role |
| Services | Placement (`orchestration-service`), `clans-service`, `ledger-service`, `sessions-service` |

## Needs

Docker, `k3d`, `kubectl`, `helm` 4.1.3 or newer, `git`, `jq`, and Python for `check.sh`.

Each Paper server uses about 2.2 GB, and the rest of the cluster about 2 GB. Every role at once needs Docker to have 12 GB or more. With less, start a few roles at a time with `ROLES`:

```bash
ROLES="hub resource" deploy/up.sh
ROLES="camp dungeon event" deploy/up.sh   # stops hub and resource
```

## Up

```bash
GHCR_USER=<github user> GHCR_TOKEN=<token with read:packages> deploy/up.sh
```

It creates the cluster `bpvp`, installs everything and starts the fleets. Run it again to upgrade in place. Servers take a few minutes to start, then join at `localhost:25565`.

| Variable | Default | What it is |
|---|---|---|
| `BPVP_TAG` | `network-preview` | The tag of every image. Any tag CI pushed, such as a commit |
| `BPVP_IMAGE` | `ghcr.io/betterpvp/server:$BPVP_TAG` | The server image, when it differs |
| `SERVICES` | `orchestration-service clans-service ledger-service sessions-service` | The services to run |
| `SERVICE_TAG` | `$BPVP_TAG` | The service and proxy images' tag, when it differs |
| `BPVP_PROXY_IMAGE` | `ghcr.io/betterpvp/proxy:$SERVICE_TAG` | The proxy image, when it differs |
| `BPVP_BUILD_LOCAL` | | `1` to build every image from this checkout first, see below |
| `BPVP_SKIP_BUILD` | | `1` with `BPVP_BUILD_LOCAL` to reuse the images it built last |
| `PLUGIN_DIR` | | With `BPVP_BUILD_LOCAL`, a folder holding the `plugins.json` jars that have no url |
| `BPVP_IMAGE_LOCAL` | | `1` to use images already on this machine instead of pulling them |
| `GHCR_USER`, `GHCR_TOKEN` | | A GitHub login that can read the image. Save the token with the team's secrets |
| `ASSETS_BUCKET`, `ASSETS_AWS_ACCESS_KEY_ID`, `ASSETS_AWS_SECRET_ACCESS_KEY`, `ASSETS_RESTIC_PASSWORD` | | Read-only build assets access. Unset, servers start without assets |
| `SERVER_MEMORY` | `3Gi` | Memory limit of each Paper server |
| `ROLES` | `hub resource camp dungeon event` | The role fleets to run. The others are stopped |
| `BUFFER_MAX` | | The most servers any role's autoscaler may run, for a small machine |
| `CLUSTER` | `bpvp` | The k3d cluster name |

The script turns these into Kubernetes secrets in the `bpvp` namespace. Nothing secret is in this folder.

### From this checkout

```bash
BPVP_BUILD_LOCAL=1 PLUGIN_DIR=~/Downloads/plugins deploy/up.sh
```

`deploy/build-local.sh` builds the suite, the proxy and every service with Gradle, then their images, and the cluster runs those. It needs no registry and no secrets.

- The images are named `bpvp.local/<name>:local`, so they never go to the team's registry. The server image holds paid plugins.
- `PLUGIN_DIR` is only read. Each `plugins.json` jar without a url is found there by its sha256. Jars with a url are downloaded.
- Without one of those jars no server starts, because Core requires PacketEvents. Dungeon servers also need FastAsyncWorldEdit and MythicMobs.
- Put the private repository at `private/` for the Dungeons, Events and Store jars. Without them the dungeon and event roles have nothing of their own to run.

## Fleets

| Fleet | Role | Replicas |
|---|---|---|
| `proxy` | Velocity | 2 |
| `hub` | hub, tagged `lobby` | 1 ready spare, 1 to 2 |
| `resource` | resource | exactly 1 |
| `camp` | camp | 1 ready spare, up to 100 |
| `dungeon` | dungeon | 1 ready spare, up to 20 |
| `event` | event | 0. Set to 1 when an event is scheduled |

```bash
kubectl -n bpvp scale fleet event --replicas 1
```

Each role has a Shulker `MinecraftServerFleet` named `<role>-template`, kept at no servers, which Shulker builds the server config from. `up.sh` copies its Agones Fleet into the role's own Fleet with the replicas, buffer and counters (`players`, `camp-slots`) from `manifests/fleets.json`, loose health, a 45 second stop grace and a console. Change replicas and buffers in `fleets.json` and server settings in `manifests/servers.yaml`, then run `up.sh` again.

Servers stay healthy for Agones while Paper runs, so a slow start or a long tick never gets one deleted.

Core reports each server Ready once Paper has started, with its counters and drain. Shulker's server agent is left out, because its earlier Ready undid allocations.

An admin command can be typed into a server's console with `kubectl -n bpvp attach -i <gameserver> -c minecraft-server`.

The proxies turn every connection away, server list pings too, while no hub is Ready.

## Proxies

The `proxy` fleet runs `ghcr.io/betterpvp/proxy`: Velocity with the BetterPvP proxy plugin (`docker/proxy`). The plugin joins the network over NATS and asks Placement which hub each joining player lands on. Its config comes from `BPVP_ORCHESTRATION_URL`, `BPVP_NATS_URL`, `BPVP_NATS_TOKEN`, `BPVP_NATS_PREFIX` and `BPVP_HUB_SITE`, or a whole `config.toml` mounted at `/config/betterpvp-proxy.toml`.

## Services

Each runs one Deployment in `bpvp`, from `manifests/services/<name>.yaml`. The `Service images` workflow pushes `ghcr.io/betterpvp/<name>` for each one on the branch.

| Service | What it needs here |
|---|---|
| `orchestration-service` | Placement, on NATS. `manifests/services/orchestration-service.toml` is its config. Its service account may list GameServers and create GameServerAllocations in `bpvp` |
| `clans-service` | One replica, so clan changes stay in order. NATS and the `betterpvp` database |
| `ledger-service` | NATS and its own `ledger` database |
| `sessions-service` | NATS |

Every service and server uses the NATS prefix `bpvp`. Change it in all of them at once.


A new service: add `manifests/services/<name>.yaml` with `__IMAGE__` and `__PULL_POLICY__` where the image goes, add it to `SERVICES` and to the `Service images` workflow's list.

Each server's name (`core.info.server`) is its GameServer's name, which Placement sends camps to.

## Look around

```bash
kubectl -n bpvp get fleets,gameservers
kubectl -n bpvp logs <gameserver pod> -c minecraft-server -f
kubectl -n bpvp exec -it postgres-1 -- psql betterpvp
```

## Check

```bash
deploy/check.sh        # every fleet in ROLES is Ready, the services run, Placement places a hub every proxy knows, localhost:25565 answers
deploy/down.sh
deploy/check.sh down   # nothing is left
```

The check asks Placement for a hub the way a joining proxy does, which allocates that hub. Joining with a Minecraft client is checked by hand.

`check.sh` also fails when Core or another BetterPvP plugin did not start on a server, since Shulker reports a server Ready either way.

`deploy/network-check.sh` checks the network by machine, one step at a time:

```bash
deploy/network-check.sh event       # event scales from 0 to 1 and back
deploy/network-check.sh placement   # Placement fills one camp server, then allocates a second
deploy/network-check.sh drain       # the first camp server, deleted, reports no room, stops in its grace and gets no new camps
deploy/network-check.sh handover    # a camp opened from the console counts, is saved when its server drains and reopens on another
deploy/network-check.sh rollout bpvp.local/server:local-2   # after up.sh with that image, every Ready server runs it
```

Run `placement`, `drain` and `handover` in that order, one at a time, on a `camp` fleet allowed two servers. Allocated servers keep their image until they stop.

`handover` opens a camp with `site create camp <owner>` on a server's console (`kubectl attach -i`). Without build assets there is no camp template, so it copies the server's own world as one.

On a machine with 8 GB for Docker, run the roles in batches with `BUFFER_MAX` so the spares fit:

```bash
BPVP_BUILD_LOCAL=1 PLUGIN_DIR=~/Downloads/plugins ROLES="hub resource" BUFFER_MAX=1 deploy/up.sh && ROLES="hub resource" deploy/check.sh
BPVP_BUILD_LOCAL=1 BPVP_SKIP_BUILD=1 ROLES="dungeon event" BUFFER_MAX=1 deploy/up.sh && deploy/network-check.sh event
BPVP_BUILD_LOCAL=1 BPVP_SKIP_BUILD=1 ROLES=camp BUFFER_MAX=2 deploy/up.sh && deploy/network-check.sh placement && deploy/network-check.sh drain && deploy/network-check.sh handover
```

## Down

```bash
deploy/down.sh
```

It deletes the cluster with its volumes and the Postgres data.
