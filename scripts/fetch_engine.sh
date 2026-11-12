#!/usr/bin/env bash
# Downloads the pinned Xray-core Android binding (libv2ray.aar) into libs/ and verifies it.
#
# The 60 MB AAR is not stored in git; it is attached to the v1.0.0 release of this
# repository. Any tag/asset pair below works, or download it from upstream directly.
set -euo pipefail

AAR_SHA256="cdfef146218233a31a8b52c8b1016557730d3b63fc121d4c84cacea50e669155"
DEST="libs/libv2ray.aar"
URL="${1:-https://github.com/sachintha82288-cpu/v2ray-vpn-android/releases/download/v1.0.0/libv2ray.aar}"

mkdir -p libs
if [ -f "$DEST" ] && echo "$AAR_SHA256  $DEST" | sha256sum -c - >/dev/null 2>&1; then
  echo "libv2ray.aar already present and verified"
  exit 0
fi

echo "Downloading $URL"
curl -fL --retry 3 -o "$DEST.part" "$URL"
echo "$AAR_SHA256  $DEST.part" | sha256sum -c -
mv "$DEST.part" "$DEST"
echo "OK: $DEST"
