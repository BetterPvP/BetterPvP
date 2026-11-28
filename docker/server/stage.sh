#!/usr/bin/env bash
# Gathers everything the server image holds into one build context.
#   docker/server/stage.sh <context-dir>
# Run from the repository root after ./gradlew shadowJar.
#
# PLUGIN_STORE_BUCKET is the bucket whose plugins/<sha256>/<file> holds the third-party and paid jars, read with the
# usual AWS credentials. PLUGIN_DIR is a local folder of jars to use instead, matched by sha256 and only read.
# A plugins.json entry with a url is downloaded when neither has it.
# With ASSETS_BUCKET, ASSETS_AWS_ACCESS_KEY_ID, ASSETS_AWS_SECRET_ACCESS_KEY and ASSETS_RESTIC_PASSWORD set, the image
# also pins the newest build assets in a release manifest.
set -euo pipefail
out=${1:?usage: stage.sh <context-dir>}
here=$(cd "$(dirname "$0")" && pwd)
list=${PLUGINS_LIST:-plugins.json}
jars=${JARS_DIR:-build/clans}
skip=" compatability "
assets_version=${ASSETS_VERSION:-v1.1.0}

rm -rf "$out"
mkdir -p "$out/bpvp/plugins"
cp "$here/Dockerfile" "$here/entrypoint.sh" "$out/"
cp "$here/roles.json" "$out/bpvp/"

found=0
for jar in "$jars"/*.jar; do
  [[ -f $jar ]] || continue
  name=$(basename "$jar" .jar)
  [[ $skip == *" $name "* ]] && continue
  cp "$jar" "$out/bpvp/plugins/"
  found=$((found + 1))
done
(( found > 0 )) || { echo "no BetterPvP jars in $jars, run ./gradlew shadowJar first" >&2; exit 1; }

# Copies the jar in PLUGIN_DIR with <sha256>, by its own name first, to <dest>.
local_copy() {
  local sha=$1 file=$2 dest=$3 candidate
  for candidate in "$PLUGIN_DIR/$file" "$PLUGIN_DIR"/*.jar; do
    [[ -f $candidate ]] || continue
    if [[ $(sha256sum "$candidate" | cut -d' ' -f1) == "$sha" ]]; then
      cp "$candidate" "$dest"
      return 0
    fi
  done
  return 1
}

# Fetches a pinned jar to <dest>: PLUGIN_DIR first, then the store, then its url. Its sha256 must match either way.
fetch() {
  local entry=$1 dest=$2 file sha url
  file=$(jq -r .file <<<"$entry")
  sha=$(jq -r .sha256 <<<"$entry")
  url=$(jq -r '.url // empty' <<<"$entry")
  if [[ -n ${PLUGIN_DIR:-} ]] && local_copy "$sha" "$file" "$dest"; then
    :
  elif [[ -n ${PLUGIN_STORE_BUCKET:-} ]] && aws s3 cp --quiet "s3://$PLUGIN_STORE_BUCKET/plugins/$sha/$file" "$dest" 2>/dev/null; then
    :
  elif [[ -n $url ]]; then
    curl -fsSL -o "$dest" "$url"
  else
    echo "$file has no url and is in neither PLUGIN_DIR (${PLUGIN_DIR:-unset}) nor the plugin store (${PLUGIN_STORE_BUCKET:-unset})" >&2
    return 1
  fi
  if ! echo "$sha  $dest" | sha256sum -c --quiet -; then
    echo "$file does not match the sha256 in $list" >&2
    return 1
  fi
}

fetch "$(jq -c .server "$list")" "$out/bpvp/paper.jar"
while IFS= read -r entry; do
  fetch "$entry" "$out/bpvp/plugins/$(jq -r .file <<<"$entry")"
done < <(jq -c '(.plugins // [])[]' "$list")

# The assets tool, and a release manifest of the newest assets when the build-assets bucket is configured.
if [[ -n ${ASSETS_BUCKET:-} ]]; then
  export AWS_ACCESS_KEY_ID=$ASSETS_AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY=$ASSETS_AWS_SECRET_ACCESS_KEY
  aws s3 cp --quiet "s3://$ASSETS_BUCKET/tools/assets/$assets_version/assets" "$out/bpvp/assets"
  chmod +x "$out/bpvp/assets"
  BUCKET=$ASSETS_BUCKET RESTIC_PASSWORD=$ASSETS_RESTIC_PASSWORD ASSETS_READ_ONLY=1 \
    "$out/bpvp/assets" release > "$out/bpvp/assets-release.json"
fi

ls -1 "$out/bpvp/plugins"
