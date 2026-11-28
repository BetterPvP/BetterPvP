#!/usr/bin/env bash
# Checks the network on the local cluster by machine, one step at a time:
#   deploy/network-check.sh event                 event scales from 0 to 1 and back
#   deploy/network-check.sh placement             Placement fills one camp server, then allocates a second
#   deploy/network-check.sh drain                 a deleted camp server drains, reports no room and stops cleanly
#   deploy/network-check.sh handover              a camp opened from the console counts, and moves when its server drains
#   deploy/network-check.sh rollout <image>       a new server image replaces every Ready server of ROLES
# Run deploy/check.sh first for the fleets, services and proxies.
set -euo pipefail
CLUSTER=${CLUSTER:-bpvp}
NS=bpvp
kubectl config use-context "k3d-$CLUSTER" >/dev/null

# jq on Windows ends lines with a carriage return.
jq() { command jq "$@" | tr -d '\r'; }
fail() { echo "FAIL: $*" >&2; exit 1; }
ok() { echo "ok: $*"; }

# Waits up to <seconds> for <command> to succeed.
wait_for() {
  local seconds=$1; shift
  local deadline=$(( $(date +%s) + seconds ))
  until "$@"; do
    (( $(date +%s) < deadline )) || return 1
    sleep 5
  done
}

# One of a GameServer's camp-slots numbers: capacity or count.
slots() { kubectl -n "$NS" get gameserver "$1" -o json 2>/dev/null | jq -r --arg k "$2" '.status.counters["camp-slots"][$k] // empty'; }
servers() { kubectl -n "$NS" get gameservers -l "agones.dev/fleet=$1" -o json; }
count_state() { servers "$1" | jq --arg s "$2" '[.items[] | select(.status.state == $s)] | length'; }
has_state() { (( $(count_state "$1" "$2") >= $3 )); }

# Asks Placement for an owned camp once per owner, from one pod, and prints each answer's payload. A request with
# no reply in time prints the outcome "no answer", since Placement may still have placed it.
place_camps() {
  local owners=$1 script=""
  for owner in $owners; do
    script+="out=\$(nats req -s nats://nats:4222 --timeout 30s bpvp.bus.placement.place "
    script+="'{\"v\":1,\"topic\":\"placement.place\",\"origin\":\"network-check\",\"correlationId\":\"camp-$owner\",\"reply\":false,"
    script+="\"payload\":{\"site\":\"camp\",\"owner\":\"$owner\",\"owned\":\"true\",\"asker\":\"network-check\"}}' 2>&1 | grep '^{');"
    script+="if [ -n \"\$out\" ]; then echo \"\$out\"; else echo '{\"payload\":{\"outcome\":\"no answer\"}}'; fi;"
  done
  # One pod for every request, run with exec: a pod per batch drops output when it ends before its log is read.
  if ! kubectl -n "$NS" get pod network-check-nats >/dev/null 2>&1; then
    kubectl -n "$NS" run network-check-nats --restart=Never --image=natsio/nats-box:0.18.1 --command -- sleep 3600 >/dev/null
  fi
  kubectl -n "$NS" wait --for=condition=Ready pod/network-check-nats --timeout=120s >/dev/null
  kubectl -n "$NS" exec network-check-nats -- sh -c "$script" | jq -c '.payload'
}

event() {
  kubectl -n "$NS" scale fleet event --replicas 1 >/dev/null
  wait_for 600 has_state event Ready 1 || fail "event did not reach one Ready server"
  ok "event scaled to 1 and its server is Ready"
  kubectl -n "$NS" scale fleet event --replicas 0 >/dev/null
  wait_for 300 bash -c "[[ \$(kubectl -n $NS get gameservers -l agones.dev/fleet=event -o name | wc -l) -eq 0 ]]" ||
    fail "event did not scale back to 0"
  ok "event scaled back to 0"
}

placement() {
  # Two camp servers at most, so the check fits a small machine.
  kubectl -n "$NS" patch fleetautoscaler camp --type merge -p '{"spec":{"policy":{"buffer":{"maxReplicas":2}}}}' >/dev/null
  wait_for 600 has_state camp Ready 1 || fail "no Ready camp server"

  local first answer server outcome capacity
  # Fresh owners every run, so no earlier lease is in the way.
  local base=$(( (RANDOM << 15 | RANDOM) * 100 ))
  answer=$(place_camps "$base")
  first=$(jq -r '.server // empty' <<<"$answer")
  [[ -n $first ]] || fail "Placement placed no camp: $answer"
  ok "Placement put the first camp on $first ($(jq -r .outcome <<<"$answer"))"
  [[ $(kubectl -n "$NS" get gameserver "$first" -o jsonpath='{.status.state}') == Allocated ]] ||
    fail "$first was not allocated"
  capacity=$(slots "$first" capacity)
  ok "$first is Allocated with $capacity camp slots"

  # The autoscaler keeps a spare beside the allocated server. Fill the first one before its promises lapse.
  wait_for 600 has_state camp Ready 1 || fail "no spare camp server came up"
  local owners=""
  for i in $(seq 1 $(( capacity + 2 ))); do owners+="$(( base + i )) "; done
  local on_first=0 unanswered=0 second=""
  while IFS= read -r answer; do
    server=$(jq -r '.server // empty' <<<"$answer")
    outcome=$(jq -r '.outcome // empty' <<<"$answer")
    if [[ $server == "$first" ]]; then
      on_first=$(( on_first + 1 ))
    elif [[ $outcome == "no answer" ]]; then
      unanswered=$(( unanswered + 1 ))
    elif [[ -n $server && $outcome == allocated ]]; then
      second=$server
      break
    else
      fail "unexpected answer after $on_first camps on $first: $answer"
    fi
  done < <(place_camps "$owners")
  [[ -n $second ]] || fail "Placement never allocated a second camp server"
  echo "$first" > "${TMPDIR:-/tmp}/bpvp-first-camp"
  local hosted
  hosted=$(slots "$first" count)
  ok "$first took $on_first more camps until full, then Placement allocated $second"
  (( unanswered == 0 )) || echo "note: $unanswered requests got no answer within 30s, and Placement may have counted them"
  # Every slot not already hosting a camp should have been offered before a second server was allocated.
  (( on_first + unanswered + 1 + hosted >= capacity )) ||
    fail "$first had $(( capacity - hosted - on_first - unanswered - 1 )) free slots left when Placement allocated $second"
  [[ $(kubectl -n "$NS" get gameserver "$second" -o jsonpath='{.status.state}') == Allocated ]] ||
    fail "$second was not allocated"
}

drain() {
  local first
  first=$(cat "${TMPDIR:-/tmp}/bpvp-first-camp" 2>/dev/null) || fail "run the placement step first"
  kubectl -n "$NS" get gameserver "$first" >/dev/null 2>&1 || fail "$first is gone already"
  local pod_before
  pod_before=$(kubectl -n "$NS" get pod "$first" -o jsonpath='{.spec.terminationGracePeriodSeconds}')
  kubectl -n "$NS" delete gameserver "$first" --wait=false >/dev/null
  # While it drains, the server reports its camp slots as full, so Placement sends it nothing new.
  local capacity="" count=""
  for _ in $(seq 60); do
    capacity=$(slots "$first" capacity 2>/dev/null || true)
    count=$(slots "$first" count 2>/dev/null || true)
    [[ -n $capacity && $capacity == "$count" ]] && break
    [[ -z $capacity ]] && break
    sleep 1
  done
  [[ -n $capacity && $capacity == "$count" ]] || fail "$first never reported its camp slots full while draining"
  ok "$first reported $count of $capacity camp slots while draining"
  local logs
  logs=$(kubectl -n "$NS" logs "$first" -c minecraft-server 2>/dev/null || true)
  wait_for 120 bash -c "! kubectl -n $NS get gameserver $first >/dev/null 2>&1" || fail "$first did not stop"
  ok "$first stopped within its ${pod_before}s grace"
  grep -qi "drain" <<<"$logs" && ok "$first logged its drain" || echo "note: no drain line in $first's log"
  local answer
  answer=$(place_camps $(( (RANDOM << 15 | RANDOM) * 100 )))
  [[ $(jq -r '.server // empty' <<<"$answer") != "$first" ]] || fail "Placement still sends camps to $first"
  ok "a new camp went to $(jq -r '.server' <<<"$answer"), not the drained $first"
}

# Runs an admin command on a server's console.
console() { timeout 20 kubectl -n "$NS" attach -i "$1" -c minecraft-server <<<"$2" >/dev/null 2>&1 || true; }

counts_at_least() { [[ $(slots "$1" count) -ge $2 ]]; }

# Without build assets a server has no camp template, so it gets a copy of its own world as one.
camp_template() {
  MSYS_NO_PATHCONV=1 kubectl -n "$NS" exec "$1" -c minecraft-server -- sh -c '
    [ -e worlds/templates/camp ] || [ -e worlds/templates/camp.polar ] && exit 0
    mkdir -p worlds/templates && cp -r worlds/world worlds/templates/camp && rm -f worlds/templates/camp/session.lock
    echo "made a camp template from its own world"' | sed "s/^/note: $1 /"
}

handover() {
  wait_for 600 has_state camp Ready 1 || fail "no Ready camp server"
  local owner=$(( (RANDOM << 15 | RANDOM) * 100 + 7 )) answer first second
  answer=$(place_camps "$owner")
  first=$(jq -r '.server // empty' <<<"$answer")
  [[ -n $first ]] || fail "Placement placed no camp: $answer"
  local before
  before=$(slots "$first" count)
  camp_template "$first"
  console "$first" "site create camp $owner"
  wait_for 120 counts_at_least "$first" $(( before + 1 )) || fail "$first did not count the camp it opened"
  ok "$first opened camp $owner, and its camp-slots count went from $before to $(slots "$first" count)"

  wait_for 600 has_state camp Ready 1 || fail "no spare camp server to hand over to"
  kubectl -n "$NS" delete gameserver "$first" --wait=false >/dev/null
  wait_for 120 bash -c "! kubectl -n $NS get gameserver $first >/dev/null 2>&1" || fail "$first did not stop"
  ok "$first drained and stopped"
  local saved
  saved=$(kubectl -n "$NS" exec postgres-1 -c postgres -- psql -U postgres -d betterpvp -At \
    -c "select version from site_records where site = 'camp' and owner = $owner" | tr -d '\r')
  [[ -n $saved ]] || fail "$first did not save camp $owner's record before stopping"
  ok "$first saved camp $owner's record (version $saved) before letting it go"
  wait_for 600 has_state camp Ready 1 || fail "no camp server to hand over to"

  answer=$(place_camps "$owner")
  second=$(jq -r '.server // empty' <<<"$answer")
  [[ -n $second && $second != "$first" ]] || fail "Placement did not move camp $owner off $first: $answer"
  ok "$first gave up camp $owner's lease, and Placement now sends it to $second ($(jq -r .outcome <<<"$answer"))"
  before=$(slots "$second" count)
  camp_template "$second"
  console "$second" "site create camp $owner"
  wait_for 120 counts_at_least "$second" $(( before + 1 )) || fail "$second did not reopen camp $owner"
  ok "$second reopened camp $owner, and its camp-slots count went from $before to $(slots "$second" count)"
}

rollout() {
  local image=${1:?usage: network-check.sh rollout <image>}
  local roles=${ROLES:-hub resource camp dungeon event}
  for role in $roles; do
    [[ $(kubectl -n "$NS" get fleet "$role" -o jsonpath='{.spec.replicas}' 2>/dev/null || echo 0) -gt 0 ]] || continue
    ready_on_new() {
      servers "$role" | jq -e --arg i "$image" '
        [.items[] | select(.status.state == "Ready")] as $ready
        | ($ready | length) > 0 and all($ready[]; .spec.template.spec.containers[] | select(.name == "minecraft-server") | .image == $i)' >/dev/null
    }
    wait_for 900 ready_on_new || fail "$role's Ready servers are not all on $image"
    local old
    old=$(servers "$role" | jq --arg i "$image" '[.items[] | select(.status.state == "Allocated")
      | select(any(.spec.template.spec.containers[]; .name == "minecraft-server" and .image != $i))] | length')
    ok "$role's Ready servers run $image${old:+ ($old Allocated keep the old image until they stop)}"
  done
}

case ${1:-} in
  event) event ;;
  placement) placement ;;
  drain) drain ;;
  handover) handover ;;
  rollout) shift; rollout "$@" ;;
  *) sed -n '2,8p' "$0"; exit 1 ;;
esac
