#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

PROJECT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$HOME/mediamtx-android-src"
ASSETS="$PROJECT/app/src/main/assets"

echo "=== Mobile Stream Server: build MediaMTX for Android ARM64 ==="
pkg update -y
pkg install -y git golang
export GOTOOLCHAIN=auto

if ! command -v go >/dev/null 2>&1; then
  echo "Go is missing. Install it with: pkg install golang"
  exit 1
fi

echo "Go toolchain:"
go version

if [ ! -d "$SRC/.git" ]; then
  git clone --depth 1 https://github.com/bluenviron/mediamtx.git "$SRC"
else
  git -C "$SRC" pull --ff-only
fi

cd "$SRC"
echo "Generating MediaMTX sources..."
go generate ./...

echo "Compiling Android/ARM64 executable..."
CGO_ENABLED=0 GOOS=android GOARCH=arm64 go build -trimpath -ldflags="-s -w" -o mediamtx-android-arm64 .

mkdir -p "$ASSETS"
cp mediamtx-android-arm64 "$ASSETS/mediamtx"
chmod 644 "$ASSETS/mediamtx"

echo
echo "SUCCESS"
ls -lh "$ASSETS/mediamtx"
echo "Android ARM64 binary copied into:"
echo "$ASSETS/mediamtx"
echo
echo "Now import/open this project in Code on the Go and build the APK."
