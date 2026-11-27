# Server image

`ghcr.io/betterpvp/server` is the one Paper image every server runs. `BPVP_ROLE` picks the role when the container starts.

The `Server image` workflow builds it on every push to `camps/main` and `network/preview` and every pull request from this repository, and pushes three tags:

| Tag | Example |
|---|---|
| The full commit | `ghcr.io/betterpvp/server:10de0408d2c1...` |
| The short commit | `ghcr.io/betterpvp/server:10de0408d2c1` |
| The branch | `ghcr.io/betterpvp/server:camps-main` |

The image holds paid plugins, so its package stays private.

## What is in it

| Path | What it is |
|---|---|
| `/opt/bpvp/paper.jar` | The Paper jar `plugins.json` pins |
| `/opt/bpvp/plugins/` | The BetterPvP jars and every third-party plugin `plugins.json` pins |
| `/opt/bpvp/roles.json` | The jars each role leaves out |
| `/opt/bpvp/assets`, `assets-release.json` | The `assets` tool and the build asset versions this image pins, when the build could read the build-assets bucket |

## Starting

The entrypoint prepares `/data` and starts Paper:

1. Installs the plugin jars, minus the ones `roles.json` drops for the role.
2. Copies every file mounted under `/config` over `/data`. `${CFG_NAME}` in a text file becomes the value of `CFG_NAME`.
3. Writes the database and server name into `plugins/Core/config.yml`.
4. Pulls the build assets when `ASSETS_BUCKET` is set.
5. Starts Paper with `--world-container worlds`.

Under Agones (`AGONES_SDK_HTTP_PORT` is set) the entrypoint also reports health every 5 seconds while Paper runs.

| Variable | Default | What it is |
|---|---|---|
| `BPVP_ROLE` | `all` | `all`, `hub`, `resource`, `camp`, `dungeon` or `event` |
| `BPVP_SERVER_NAME` | the pod or host name | `core.info.server` |
| `BPVP_DB_HOST` | | Postgres as `host:port`. Unset, the database settings are left as they are |
| `BPVP_DB_NAME` | `betterpvp` | |
| `BPVP_DB_USER` | `betterpvp` | |
| `BPVP_DB_PASSWORD` or `BPVP_DB_PASSWORD_FILE` | | |
| `BPVP_DB_POOL` | `10` | |
| `BPVP_CONFIG_SET` | | More settings, one per line: `<file> <yq path> <value>` |
| `BPVP_CONFIG_DIR` | `/config` | Files copied over the server folder |
| `BPVP_SERVER_DIR` | `/data` | The server folder |
| `ASSETS_BUCKET`, `ASSETS_REGION` | | The build-assets bucket. Unset, no assets are pulled |
| `ASSETS_AWS_ACCESS_KEY_ID`, `ASSETS_AWS_SECRET_ACCESS_KEY`, `ASSETS_RESTIC_PASSWORD` | | Read-only credentials for it |
| `ASSETS_RELEASE` | the image's pinned release | A release manifest path, or `latest` for the newest assets |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75 -XX:+UseG1GC` | |
| `SERVER_ARGS` | | More Paper arguments |

```bash
BPVP_CONFIG_SET='plugins/Core/config.yml .core.network.bus nats
plugins/Clans/config.yml .clans.example 5'
```

Configs come from the environment and mounted files. The image holds no secrets.

## Building it locally

```bash
./gradlew shadowJar
PLUGIN_STORE_BUCKET=<bucket> docker/server/stage.sh build/image
docker build -t ghcr.io/betterpvp/server:local build/image
docker/server/check.sh ghcr.io/betterpvp/server:local
```

`stage.sh` takes the paid and url-less jars from `PLUGIN_DIR`, a local folder it only reads, or else needs AWS credentials that can read the plugin store (`plugins/<sha256>/<file>` in the playtest bucket, see BetterPvP/playtest `docs/plugins.md`). A jar with a url is downloaded when the store does not have it. Every jar is checked against its sha256.

`check.sh` starts the image as `all` against the docker-compose Postgres in a database of its own and waits for `Done`. It then checks a `camp` server has no MythicMobs or Dungeons jar.

## Changing what a role runs

`roles.json` lists, per role, globs of jars to leave out. Case is ignored.

A jar that hard-depends on a dropped one goes too, or Paper refuses to load it. Store needs MCPets, which needs MythicMobs, so camp servers leave out all three.

A `camp` server sets its own player limit when it starts: `core.agones.campSlots` (6) times `clans.members.max` (8) plus `clans.camp.visitors` (4), which is 72. Every other role keeps the limit in `server.properties`.

## Setup

The workflow needs these in the repository settings. Save each key with the team's secrets.

| Name | Kind | What it is |
|---|---|---|
| `PRIVATE_DEPLOY_KEY` | secret | A read-only deploy key for BetterPvP/BetterPvP-Private |
| `PLUGIN_STORE_BUCKET` | variable | The playtest bucket holding the plugin store |
| `PLUGIN_STORE_AWS_ACCESS_KEY_ID`, `PLUGIN_STORE_AWS_SECRET_ACCESS_KEY` | secret | A key that can read `plugins/` in it |
| `ASSETS_BUCKET` | variable | Optional. The build-assets bucket, to pin assets in the image |
| `ASSETS_AWS_ACCESS_KEY_ID`, `ASSETS_AWS_SECRET_ACCESS_KEY`, `ASSETS_RESTIC_PASSWORD` | secret | Optional. Its read-only key and repository password |

```bash
R=BetterPvP/BetterPvP
gh secret set PRIVATE_DEPLOY_KEY -R $R < deploy_key
gh variable set PLUGIN_STORE_BUCKET -R $R --body <bucket>
gh secret set PLUGIN_STORE_AWS_ACCESS_KEY_ID -R $R
gh secret set PLUGIN_STORE_AWS_SECRET_ACCESS_KEY -R $R
```
