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

VARIANT="${VARIANT:-freeBundleRelease}"
TASK_SUFFIX="$(tr '[:lower:]' '[:upper:]' <<<"${VARIANT:0:1}")${VARIANT:1}"
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
    -dname "CN=Retromul, OU=Dev, O=Retromul, L=NA, ST=NA, C=US"
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
mkdir -p artifacts

FLAVOR_DIR="${VARIANT%Release}"
APK_PATH="$(find "${OUT_DIR}/${FLAVOR_DIR}/release" -type f -name "*.apk" | sort | tail -n 1 || true)"
if [[ -z "${APK_PATH}" ]]; then
  APK_PATH="$(find "${OUT_DIR}" -type f -name "*release*.apk" | sort | tail -n 1 || true)"
fi
if [[ -z "${APK_PATH}" ]]; then
  echo "Aucun APK trouvé pour la variante ${VARIANT}."
  exit 1
fi

cp -f "${APK_PATH}" "artifacts/Retromul-${VARIANT}.apk"
echo "APK généré: artifacts/Retromul-${VARIANT}.apk"
