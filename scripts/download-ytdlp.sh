#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

ARM64_DIR="$PROJECT_ROOT/app/src/main/jniLibs/arm64-v8a"
X86_DIR="$PROJECT_ROOT/app/src/main/jniLibs/x86_64"

mkdir -p "$ARM64_DIR" "$X86_DIR"

echo "⬇  Downloading yt-dlp ARM64 (for physical Android devices)..."
curl -L --retry 3 --retry-delay 2 --connect-timeout 30 --max-time 300 \
  "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_android" \
  -o "$ARM64_DIR/libytdlp.so"

FILE_SIZE=$(wc -c < "$ARM64_DIR/libytdlp.so")
if [ "$FILE_SIZE" -lt 1000000 ]; then
  echo "✗ Download failed — file too small ($FILE_SIZE bytes)"
  rm "$ARM64_DIR/libytdlp.so"
  exit 1
fi
echo "✓ ARM64 binary: $(du -sh "$ARM64_DIR/libytdlp.so" | cut -f1)"

echo "⬇  Downloading yt-dlp x86_64 (for emulators)..."
curl -L --retry 3 --retry-delay 2 --connect-timeout 30 --max-time 300 \
  "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux" \
  -o "$X86_DIR/libytdlp.so" || echo "⚠  x86_64 download failed (optional)"

echo ""
echo "✓ Done. Now run: ./gradlew assembleDebug"
