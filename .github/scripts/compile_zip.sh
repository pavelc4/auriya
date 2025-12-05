#!/bin/bash
set -e

# Define paths
BUILD_DIR="build/release"
MODULE_DIR="module"
TARGET_BIN="target/aarch64-linux-android/release/auriya"
WEBUI_DIST="webui/dist"

# Create build directory
mkdir -p "$BUILD_DIR"
mkdir -p "$BUILD_DIR/module"

# Copy module files
cp -r "$MODULE_DIR"/* "$BUILD_DIR/module/"

# Copy binary
mkdir -p "$BUILD_DIR/module/system/bin"
cp "$TARGET_BIN" "$BUILD_DIR/module/system/bin/"

# Copy WebUI
mkdir -p "$BUILD_DIR/module/webroot"
cp -r "$WEBUI_DIST"/* "$BUILD_DIR/module/webroot/"

# Copy configs
cp settings.toml "$BUILD_DIR/module/"
cp gamelist.toml "$BUILD_DIR/module/"

# Get version
VERSION=$(grep '^version=' "$MODULE_DIR/module.prop" | cut -d'=' -f2)
COMMIT_HASH=$(git rev-parse --short HEAD)
ZIP_NAME="auriya-v${VERSION}-${COMMIT_HASH}.zip"

# Zip it
cd "$BUILD_DIR/module"

if command -v 7z >/dev/null 2>&1; then
    echo "Using 7-Zip ultra compression..."
    # -mx=9: Ultra compression
    # -mfb=258: Max fast bytes
    # -mpass=15: Max passes
    7z a -tzip -mx=9 -mfb=258 -mpass=15 "../$ZIP_NAME" .
else
    echo "Using standard zip..."
    zip -r9 "../$ZIP_NAME" .
fi

if command -v advzip >/dev/null 2>&1; then
    echo "Applying advzip optimization..."
    advzip -z -4 "../$ZIP_NAME"
fi

cd -

echo "::set-output name=zipName::build/release/$ZIP_NAME"
echo "Created $ZIP_NAME"
