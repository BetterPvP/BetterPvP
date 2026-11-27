#!/usr/bin/env bash
# Prepares the server folder for BPVP_ROLE and starts Paper. See docker/server/README.md for every variable.
set -euo pipefail
shopt -s nullglob nocaseglob

image=/opt/bpvp
dir=${BPVP_SERVER_DIR:-/data}
role=${BPVP_ROLE:-all}
mkdir -p "$dir/plugins"
cd "$dir"

jq -e --arg r "$role" 'has($r)' "$image/roles.json" >/dev/null ||
  { echo "unknown BPVP_ROLE '$role', expected one of: $(jq -r 'keys | join(", ")' "$image/roles.json")" >&2; exit 1; }

# Jars a previous start installed go first, so a new image or role never leaves an old one behind.
if [[ -f .bpvp-jars ]]; then
  while IFS= read -r jar; do rm -f "plugins/$jar"; done < .bpvp-jars
fi
: > .bpvp-jars
for jar in "$image"/plugins/*.jar; do
  name=$(basename "$jar")
  dropped=false
  while IFS= read -r glob; do
    # shellcheck disable=SC2053
    [[ ${name,,} == ${glob,,} ]] && dropped=true
  done < <(jq -r --arg r "$role" '.[$r][]' "$image/roles.json")
  $dropped && continue
  cp "$jar" plugins/
  echo "$name" >> .bpvp-jars
done
cp "$image/paper.jar" paper.jar
echo "eula=true" > eula.txt

# Mounted files, such as a ConfigMap or Secret, are copied over the folder as they are.
config_dir=${BPVP_CONFIG_DIR:-/config}
if [[ -d $config_dir ]]; then
  # Kubernetes keeps a mount's real files in ..data folders, which are skipped.
  (cd "$config_dir" && find -L . -path './..*' -prune -o -type f -print) | while IFS= read -r file; do
    mkdir -p "$(dirname "$file")"
    cp -L "$config_dir/$file" "$file"
    # ${CFG_NAME} in a text file becomes the CFG_NAME variable, as Shulker's forwarding secret expects.
    case $file in
      *.yml|*.yaml|*.properties|*.toml|*.json|*.txt|*.conf)
        perl -pi -e 's/\$\{(CFG_\w+)\}/exists $ENV{$1} ? $ENV{$1} : $&/ge' "$file" ;;
    esac
  done
fi

# Sets <path> in a YAML file to the string or number in <value>, making the file when it is missing.
set_yaml() {
  local file=$1 path=$2
  mkdir -p "$(dirname "$file")"
  [[ -f $file ]] || : > "$file"
  VALUE=$3 yq -i "$path = (strenv(VALUE) | from_yaml)" "$file"
}

core=plugins/Core/config.yml
set_yaml "$core" .core.info.server "${BPVP_SERVER_NAME:-${SHULKER_SERVER_NAME:-${HOSTNAME:-$role}}}"
if [[ -n ${BPVP_DB_HOST:-} ]]; then
  password=${BPVP_DB_PASSWORD:-}
  [[ -n ${BPVP_DB_PASSWORD_FILE:-} ]] && password=$(< "$BPVP_DB_PASSWORD_FILE")
  set_yaml "$core" .core.database.global.ip "$BPVP_DB_HOST"
  set_yaml "$core" .core.database.global.databaseName "${BPVP_DB_NAME:-betterpvp}"
  set_yaml "$core" .core.database.global.username "${BPVP_DB_USER:-betterpvp}"
  VALUE=$password yq -i '.core.database.global.password = strenv(VALUE)' "$core"
  set_yaml "$core" .core.database.global.maxPoolSize "${BPVP_DB_POOL:-10}"
fi

# Any other setting, one per line: <file> <yq path> <value>, e.g. "plugins/Core/config.yml .core.redis.enabled false".
while read -r file path value; do
  [[ -n $file ]] && set_yaml "$file" "$path" "$value"
done <<<"${BPVP_CONFIG_SET:-}"

# Build assets: structures into plugins/Core/schematics, each world into worlds/<name>. The image pins a release;
# ASSETS_RELEASE=latest pulls the newest instead.
if [[ -n ${ASSETS_BUCKET:-} ]]; then
  : "${ASSETS_AWS_ACCESS_KEY_ID:?}" "${ASSETS_AWS_SECRET_ACCESS_KEY:?}" "${ASSETS_RESTIC_PASSWORD:?}"
  region=${ASSETS_REGION:-us-east-2}
  tool=$image/assets
  if [[ ! -x $tool ]]; then
    tool=$(mktemp)
    curl -fsS --aws-sigv4 "aws:amz:$region:s3" --user "$ASSETS_AWS_ACCESS_KEY_ID:$ASSETS_AWS_SECRET_ACCESS_KEY" \
      -o "$tool" "https://$ASSETS_BUCKET.s3.$region.amazonaws.com/tools/assets/${ASSETS_VERSION:-v1.1.0}/assets"
    chmod +x "$tool"
  fi
  release=()
  if [[ ${ASSETS_RELEASE:-} != latest ]] && [[ -f ${ASSETS_RELEASE:-$image/assets-release.json} ]]; then
    release=(--release "${ASSETS_RELEASE:-$image/assets-release.json}")
  fi
  AWS_ACCESS_KEY_ID=$ASSETS_AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY=$ASSETS_AWS_SECRET_ACCESS_KEY BUCKET=$ASSETS_BUCKET \
    REGION=$region RESTIC_PASSWORD=$ASSETS_RESTIC_PASSWORD ASSETS_READ_ONLY=1 RESTIC_CACHE_DIR=/tmp/restic \
    "$tool" pull . "${release[@]}"
fi

echo "Starting $role on build ${BPVP_REVISION:-dev} with $(wc -l < .bpvp-jars) plugin jars"
# Under Agones, health follows the Paper process, so a slow start or a long tick never gets the server deleted.
if [[ -n ${AGONES_SDK_HTTP_PORT:-} ]]; then
  (
    while kill -0 $$ 2>/dev/null; do
      curl -fsS -m 2 -o /dev/null -X POST -H 'Content-Type: application/json' -d '{}' "http://localhost:$AGONES_SDK_HTTP_PORT/health" 2>/dev/null || true
      sleep 5
    done
  ) &
fi

# shellcheck disable=SC2086
exec java ${JAVA_OPTS:--XX:MaxRAMPercentage=75 -XX:+UseG1GC} -jar paper.jar --nogui --world-container worlds ${SERVER_ARGS:-}
