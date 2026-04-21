#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [[ -n "${1:-}" ]]; then
    VARIANT="$1"
else
    # Use latest changelog version for output naming when no explicit variant is provided.
    if [[ -f "changelog.md" ]]; then
        CHANGELOG_VERSION="$(sed -n '2p' changelog.md | awk -F ' - ' '{print $NF}' | xargs || true)"
        VARIANT="${CHANGELOG_VERSION:-latest}"
    else
        VARIANT="latest"
    fi
fi

BUILD_VARIANT="${2:-freeBundleRelease}"
SERVICE_NAME="phoenix-down-build"

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
export VARIANT_LABEL="${VARIANT}"
export BUILD_VARIANT

chmod +x src/scripts/build-release-inside.sh

"${COMPOSE[@]}" build "${SERVICE_NAME}"
"${COMPOSE[@]}" run --rm "${SERVICE_NAME}"

echo "Build terminé. APK final: releases/phoenix-down-${VARIANT}.apk"
