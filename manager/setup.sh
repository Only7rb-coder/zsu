#!/usr/bin/env bash
set -euo pipefail

: "${MY_KEYSTORE_PASSWORD:?Set MY_KEYSTORE_PASSWORD}"
: "${MY_KEY_ALIAS:?Set MY_KEY_ALIAS}"
: "${MY_KEY_PASSWORD:?Set MY_KEY_PASSWORD}"
test -f key.jks

# Pass signing settings directly to Gradle without rewriting the user's
# gradle.properties or printing secret values.
export ORGANIZATION_GRADLE_PROJECT_KEYSTORE_FILE="key.jks"
export ORGANIZATION_GRADLE_PROJECT_KEYSTORE_PASSWORD="$MY_KEYSTORE_PASSWORD"
export ORGANIZATION_GRADLE_PROJECT_KEY_ALIAS="$MY_KEY_ALIAS"
export ORGANIZATION_GRADLE_PROJECT_KEY_PASSWORD="$MY_KEY_PASSWORD"

./gradlew clean assembleRelease
