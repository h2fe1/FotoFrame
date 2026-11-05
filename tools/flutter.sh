#!/usr/bin/env bash
# Flutter 环境封装：把工作区内隔离的 HOME/PUB_CACHE/XDG 注入后再调 flutter。
# 用法：tools/flutter.sh run -d macos / tools/flutter.sh build macos / tools/flutter.sh test
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export HOME="$ROOT/.toolchain/home"
export PUB_CACHE="$ROOT/.toolchain/pub-cache"
export XDG_CONFIG_HOME="$ROOT/.toolchain/config"
export XDG_CACHE_HOME="$ROOT/.toolchain/cache"
exec "$ROOT/.toolchain/flutter/bin/flutter" "$@"
