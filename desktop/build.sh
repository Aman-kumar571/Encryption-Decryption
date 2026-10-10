#!/bin/bash
# Build script for FileCrypt desktop app
# Requires Java 17+

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "Compiling Java sources..."
mkdir -p build
javac -d build -source 17 -target 17 src/com/filecrypt/*.java

echo "Creating JAR..."
mkdir -p dist
jar cfe dist/FileCrypt.jar com.filecrypt.Main -C build .

echo "Build complete: dist/FileCrypt.jar"

# Compute SHA-256 checksum
if command -v sha256sum &> /dev/null; then
    sha256sum dist/FileCrypt.jar
elif command -v shasum &> /dev/null; then
    shasum -a 256 dist/FileCrypt.jar
fi