#!/bin/bash
# Setup script for AxBrowser - generates Gradle wrapper if missing
set -e

GRADLE_VERSION="8.6"
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

# Check if wrapper jar exists
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Gradle wrapper JAR not found. Generating..."
    
    # Create wrapper directory
    mkdir -p gradle/wrapper
    
    # Download the wrapper JAR from a known good source
    WRAPPER_JAR_URL="https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar"
    
    if command -v curl &> /dev/null; then
        curl -sL -o "$WRAPPER_JAR" "$WRAPPER_JAR_URL" || true
    elif command -v wget &> /dev/null; then
        wget -q -O "$WRAPPER_JAR" "$WRAPPER_JAR_URL" || true
    fi
    
    # If download failed, create a minimal wrapper script
    if [ ! -f "$WRAPPER_JAR" ] || [ ! -s "$WRAPPER_JAR" ]; then
        echo "Could not download wrapper JAR. Using gradle/actions/setup-gradle instead."
        exit 0
    fi
fi

chmod +x gradlew
echo "Gradle wrapper setup complete."
