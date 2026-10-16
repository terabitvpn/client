#!/usr/bin/env bash
# Builds the Android mihomo core from legiz-ru/Prizrak-Box-android (ClashMetaForAndroid fork on
# Prizrak-Core): its `core` (Go + JNI bridge, libclash.so) and `common` modules as AARs into
# androidApp/libs. The commit is pinned in gradle/libs.versions.toml (prizrakAndroid).
# Needs Go, JDK 21 and the Android SDK/NDK (CI has them; locally: WSL or Linux/macOS).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REF="$(sed -nE 's/^prizrakAndroid[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$ROOT/gradle/libs.versions.toml")"
LIBS="$ROOT/androidApp/libs"
WORK="${PRIZRAK_WORK:-$ROOT/build/prizrak-android}"

if [ -f "$LIBS/mihomo-core.aar" ] && [ -f "$LIBS/mihomo-common.aar" ] && [ "$(cat "$LIBS/.mihomo-ref" 2>/dev/null)" = "$REF" ]; then
    echo "mihomo core $REF is up to date"
    exit 0
fi

echo "Building mihomo core from Prizrak-Box-android@$REF"
rm -rf "$WORK"
git clone --quiet https://github.com/legiz-ru/Prizrak-Box-android.git "$WORK"
git -C "$WORK" checkout --quiet "$REF"

(cd "$WORK/core/src/foss/golang" && go mod download && go mod tidy)
(cd "$WORK" && chmod +x gradlew && ./gradlew --no-daemon --console=plain :core:assembleMetaRelease :common:assembleMetaRelease     -x :core:verifyMetaReleaseResources -x :common:verifyMetaReleaseResources)

mkdir -p "$LIBS"
cp "$WORK/core/build/outputs/aar/core-meta-release.aar" "$LIBS/mihomo-core.aar"
cp "$WORK/common/build/outputs/aar/common-meta-release.aar" "$LIBS/mihomo-common.aar"
echo "$REF" > "$LIBS/.mihomo-ref"
echo "mihomo core ready: $LIBS/mihomo-core.aar"
