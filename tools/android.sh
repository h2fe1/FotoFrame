#!/usr/bin/env bash
# Build / test the native Android app with the right JDK + SDK.
# Usage: tools/android.sh assembleDebug | testDebugUnitTest | installDebug
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ -z "${JAVA_HOME:-}" ]; then
  candidates=(
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    "/opt/android-studio/jbr"
    "/usr/lib/jvm/java-21-openjdk-amd64"
    "/usr/lib/jvm/java-17-openjdk-amd64"
  )
  for candidate in "${candidates[@]}"; do
    if [ -x "$candidate/bin/java" ]; then
      JAVA_HOME="$candidate"
      break
    fi
  done
fi
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "找不到 JDK。请安装 JDK 17+ 并设置 JAVA_HOME。" >&2
  exit 1
fi
export JAVA_HOME

if [ -z "${ANDROID_HOME:-}" ]; then
  for candidate in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" "/opt/android-sdk"; do
    if [ -d "$candidate" ]; then
      ANDROID_HOME="$candidate"
      break
    fi
  done
fi
if [ -z "${ANDROID_HOME:-}" ] || [ ! -d "$ANDROID_HOME" ]; then
  echo "找不到 Android SDK。请设置 ANDROID_HOME。" >&2
  exit 1
fi
export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"

exec "$ROOT/android/gradlew" -p "$ROOT/android" "$@"
