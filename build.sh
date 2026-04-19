#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

VARIANT="${1:-v1.0.0}"
SERVICE_NAME="retromul-build"

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE=(docker-compose)
else
    echo "docker compose (ou docker-compose) est requis."
    exit 1
fi

export HOST_UID="$(id -u)"
export HOST_GID="$(id -g)"
export VARIANT

chmod +x src/scripts/build-release-inside.sh

"${COMPOSE[@]}" build "${SERVICE_NAME}"
"${COMPOSE[@]}" run --rm "${SERVICE_NAME}"

echo "Build terminé. APK final: artifacts/Retromul-${VARIANT}.apk"
