#!/usr/bin/env bash
# e2e scenario: bot in API mode + sentence qualification in Tock Studio.
#
#   ./e2e/run-e2e.sh [--build] [--keep]
#
#   --build  builds tock (../tock), then the SNAPSHOT images of the services that use Mongo
#   --keep   keeps the stack running at the end (otherwise: docker compose down -v)
#
# Variables: TAG (tested images, default: tock-docker pom version, i.e. the version of the built images), STABLE_TAG (duckling, kotlin_compiler,
# demo bot, default 26.9.1), TOCK_DIR (default ../tock), BUILDX_BUILDER (default mybuilder).
set -euo pipefail

E2E_DIR="$(cd "$(dirname "$0")" && pwd)"
DOCKER_DIR="$(dirname "$E2E_DIR")"
TOCK_DIR="${TOCK_DIR:-$(dirname "$DOCKER_DIR")/tock}"
export TAG="${TAG:-$(mvn -q -f "$DOCKER_DIR/pom.xml" -N help:evaluate -Dexpression=project.version -DforceStdout)}"
export STABLE_TAG="${STABLE_TAG:-26.9.1}"
export MONGO="${MONGO:-latest}"
export PLATFORM="${PLATFORM:-}"
PROJECT=tock-e2e

BUILD=false
KEEP=false
for arg in "$@"; do
  case "$arg" in
    --build) BUILD=true ;;
    --keep) KEEP=true ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

compose() {
  docker compose -p "$PROJECT" --project-directory "$DOCKER_DIR" \
    -f "$DOCKER_DIR/docker-compose-bot-webhook.yml" -f "$E2E_DIR/docker-compose.e2e.yml" "$@"
}

if $BUILD; then
  TOCK_VERSION="$(mvn -q -f "$TOCK_DIR/pom.xml" help:evaluate -Dexpression=project.version -DforceStdout)"
  echo "== build tock $TOCK_VERSION ($TOCK_DIR)"
  mvn -f "$TOCK_DIR/pom.xml" install -DskipTests -Dmaven.javadoc.skip=true -Ddokka.skip=true \
    -pl ai.tock:tock-bot-admin-web,ai.tock:tock-nlp-api-doc,ai.tock:tock-bot-admin-server,ai.tock:tock-nlp-admin-server,ai.tock:tock-nlp-model-rasa,ai.tock:tock-nlp-api-service,ai.tock:tock-nlp-build-model-worker,ai.tock:tock-nlp-build-model-worker-on-aws-batch,ai.tock:tock-bot-api-service \
    -am
  echo "== build images $TAG"
  # multi-platform buildx: only the native image is loaded into the local daemon
  BUILDX_BUILDER="${BUILDX_BUILDER:-mybuilder}" mvn -f "$DOCKER_DIR/pom.xml" \
    -Dtock="$TOCK_VERSION" -pl bot-admin,nlp-api,nlp-build-model-worker,bot-api \
    package docker:build
fi

for port in 80 8080 8887 8888 27017 27018 27019; do
  if (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null; then
    echo "port $port already in use: stop the stack using it" >&2
    exit 1
  fi
done

# Non-blocking report of the tock services errors: known errors are listed too.
report_errors() {
  mkdir -p "$E2E_DIR/logs"
  compose logs --no-color > "$E2E_DIR/logs/compose.log" 2>&1 || true
  local report="$E2E_DIR/logs/errors.txt"
  : > "$report"
  for service in admin_web nlp_api build_worker bot_api demo_api_webhook; do
    compose logs --no-color --no-log-prefix "$service" 2>/dev/null \
      | grep -E " ERROR |^[a-zA-Z.]+(Exception|Error)\b" \
      | sed -E 's/^[0-9T:.-]+ \[[^]]*\] //; s/[0-9a-f]{24}/<id>/g; s/[0-9]+/<n>/g' \
      | cut -c1-200 | sort | uniq -c | sort -rn | sed "s/^/$service: /" >> "$report" || true
  done
  echo "== errors in the logs (non-blocking, details: $E2E_DIR/logs/compose.log)"
  if [ -s "$report" ]; then cat "$report"; else echo "none"; fi
}

cleanup() {
  status=$?
  report_errors
  if $KEEP; then
    echo "stack kept (project $PROJECT)"
  else
    compose down -v --remove-orphans >/dev/null 2>&1 || true
  fi
  exit $status
}
trap cleanup EXIT

echo "== startup (TAG=$TAG, STABLE_TAG=$STABLE_TAG)"
compose up -d

wait_for() {
  local url=$1 deadline=$((SECONDS + 300))
  printf 'waiting for %s ' "$url"
  until curl -sf -o /dev/null "$url"; do
    if [ $SECONDS -gt $deadline ]; then echo " timeout"; return 1; fi
    printf '.'; sleep 3
  done
  echo " ok"
}
wait_services() {
  wait_for http://localhost/rest/admin/healthcheck
  wait_for http://localhost:8888/rest/nlp/healthcheck
  wait_for http://localhost:8080/healthcheck
  wait_for http://localhost:8887/healthcheck
}
wait_services

echo "== Playwright tests (Kotlin)"
mvn -f "$E2E_DIR/pom.xml" test

echo "== restart of the tock services"
compose restart admin_web nlp_api build_worker bot_api
wait_services

echo "== tests after restart"
mvn -f "$E2E_DIR/pom.xml" test -Dtest=AfterRestartTest
