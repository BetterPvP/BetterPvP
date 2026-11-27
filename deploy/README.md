# Local cluster

One command runs the whole network on your machine in a k3d cluster:

| Piece | Version |
|---|---|
| k3s | v1.34.13-k3s1 |
| Agones | 1.62.0 |
| Shulker | v0.13.0 |
| NATS | chart 2.15.3, 3 nodes with JetStream |
| CloudNativePG | chart 0.29.2, one Postgres instance |
| Velocity | 2 proxies, on `localhost:25565` |
| Paper | the server image, one fleet per role |

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
| `BPVP_IMAGE` | `ghcr.io/betterpvp/server:camps-main` | The server image. Any tag CI pushed, such as a commit |
| `BPVP_IMAGE_LOCAL` | | `1` to use an image built on this machine instead of pulling it |
| `GHCR_USER`, `GHCR_TOKEN` | | A GitHub login that can read the image. Save the token with the team's secrets |
| `ASSETS_BUCKET`, `ASSETS_AWS_ACCESS_KEY_ID`, `ASSETS_AWS_SECRET_ACCESS_KEY`, `ASSETS_RESTIC_PASSWORD` | | Read-only build assets access. Unset, servers start without assets |
| `SERVER_MEMORY` | `3Gi` | Memory limit of each Paper server |
| `ROLES` | `hub resource camp dungeon event` | The role fleets to run. The others are stopped |
| `CLUSTER` | `bpvp` | The k3d cluster name |

The script turns these into Kubernetes secrets in the `bpvp` namespace. Nothing secret is in this folder.

A local image:

```bash
docker build -t ghcr.io/betterpvp/server:local build/image   # see docker/server/README.md
BPVP_IMAGE=ghcr.io/betterpvp/server:local BPVP_IMAGE_LOCAL=1 deploy/up.sh
```

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

Change a fleet on its `MinecraftServerFleet`, never on the Agones `Fleet`, because Shulker writes the Fleet back. `manifests/fleets.yaml` adds the Agones counters (`players`, `camp-slots`) and the stop grace beside Shulker's fields.

Servers stay healthy for Agones while Paper runs, so a slow start or a long tick never gets one deleted.

The proxies turn every connection away, server list pings too, while no hub is Ready.

## Look around

```bash
kubectl -n bpvp get fleets,gameservers
kubectl -n bpvp logs <gameserver pod> -c minecraft-server -f
kubectl -n bpvp exec -it postgres-1 -- psql betterpvp
```

## Check

```bash
deploy/check.sh        # every fleet in ROLES reaches Ready, localhost:25565 answers and every proxy has a hub
deploy/down.sh
deploy/check.sh down   # nothing is left
```

## Down

```bash
deploy/down.sh
```

It deletes the cluster with its volumes and the Postgres data.
