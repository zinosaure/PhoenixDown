#!/usr/bin/env bash
set -euo pipefail

cd /workspace

mkdir -p "${HOME:-/workspace}/.android"

LOCAL_PROPS_BACKUP=""
if [[ -f "local.properties" ]]; then
  LOCAL_PROPS_BACKUP="$(mktemp)"
  cp local.properties "${LOCAL_PROPS_BACKUP}"
fi

cleanup() {
  if [[ -n "${LOCAL_PROPS_BACKUP}" && -f "${LOCAL_PROPS_BACKUP}" ]]; then
    cp "${LOCAL_PROPS_BACKUP}" local.properties
    rm -f "${LOCAL_PROPS_BACKUP}"
  fi
}
trap cleanup EXIT

BUILD_VARIANT="${BUILD_VARIANT:-${VARIANT:-freeBundleRelease}}"
VARIANT_LABEL="${VARIANT_LABEL:-${VARIANT:-${BUILD_VARIANT}}}"
TASK_SUFFIX="$(tr '[:lower:]' '[:upper:]' <<<"${BUILD_VARIANT:0:1}")${BUILD_VARIANT:1}"
TASK=":lemuroid-app:assemble${TASK_SUFFIX}"

if [[ ! -f "local.properties" ]] || ! grep -q "sdk.dir=/opt/android-sdk" local.properties; then
  cat > local.properties <<'EOF'
sdk.dir=/opt/android-sdk
THEGAMESDB_API_KEY=
EOF
fi

if [[ ! -f "release.jks" ]]; then
  keytool -genkeypair \
    -keystore release.jks \
    -storepass lemuroid \
    -keypass lemuroid \
    -alias lemuroid \
    -keyalg RSA \
    -keysize 2048 \
    -validity 36500 \
    -dname "CN=Phoenix Down, OU=Dev, O=Phoenix Down, L=NA, ST=NA, C=US"
fi

if [[ ! -f "debug.keystore" ]]; then
  keytool -genkeypair \
    -keystore debug.keystore \
    -storepass android \
    -keypass android \
    -alias androiddebugkey \
    -keyalg RSA \
    -keysize 2048 \
    -validity 36500 \
    -dname "CN=Android Debug, OU=Android, O=Android, L=NA, ST=NA, C=US"
fi

bash ./gradlew --no-daemon --stacktrace "${TASK}"

OUT_DIR="lemuroid-app/build/outputs/apk"
mkdir -p releases

BUILD_TYPE="release"
if [[ "${BUILD_VARIANT}" == *Debug ]]; then
  BUILD_TYPE="debug"
fi

FLAVOR_DIR="${BUILD_VARIANT%Release}"
if [[ "${FLAVOR_DIR}" == "${BUILD_VARIANT}" ]]; then
  FLAVOR_DIR="${BUILD_VARIANT%Debug}"
fi

APK_PATH="$(find "${OUT_DIR}/${FLAVOR_DIR}/${BUILD_TYPE}" -type f -name "*.apk" | sort | tail -n 1 || true)"
if [[ -z "${APK_PATH}" ]]; then
  APK_PATH="$(find "${OUT_DIR}" -type f -name "*${BUILD_TYPE}*.apk" | sort | tail -n 1 || true)"
fi
if [[ -z "${APK_PATH}" ]]; then
  APK_PATH="$(find "${OUT_DIR}" -type f -name "*.apk" | sort | tail -n 1 || true)"
fi
if [[ -z "${APK_PATH}" ]]; then
  echo "Aucun APK trouvé pour la variante ${BUILD_VARIANT}."
  exit 1
fi

cp -f "${APK_PATH}" "releases/phoenix-down-${VARIANT_LABEL}.apk"
echo "APK généré: releases/phoenix-down-${VARIANT_LABEL}.apk"
