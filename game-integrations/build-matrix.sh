#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DIST="$ROOT/dist"
MVN="${MAVEN_CMD:-mvn}"
GRADLE="${GRADLE_CMD:-gradle}"
DRY_RUN=0
[[ "${1:-}" == "--dry-run" ]] && DRY_RUN=1

build_gradle() {
  local dir="$1"; shift
  local loader="$1"; shift
  local family="$1"; shift
  echo "  gradle $*"
  [[ "$DRY_RUN" == "1" ]] && return 0
  (cd "$ROOT/$dir" && "$GRADLE" "$@")
  mkdir -p "$DIST/$loader/$family"
  find "$ROOT/$dir/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*sources*' \
    -exec cp -f {} "$DIST/$loader/$family/" \;
}

echo "Minecraft server report build matrix"
echo "Paper: 1.18.2 -> 26.3 (universal)"
if [[ "$DRY_RUN" == "0" ]]; then
  (cd "$ROOT" && "$MVN" -q -DskipTests package)
  mkdir -p "$DIST/paper/universal"
  cp -f "$ROOT/paper/target/minecraft-server-report-paper.jar" "$DIST/paper/universal/"
fi

echo "Fabric 1.18 / 1.19 / 1.20"
build_gradle fabric fabric 1.18 \
  -Pminecraft_family=1.18 -Pminecraft_version=1.18.2 -Pminecraft_range='>=1.18 <1.19' \
  -Pyarn_mappings=1.18.2+build.4 -Ploader_version=0.14.24 -Pfabric_api_version=0.77.0+1.18.2 \
  -Ploom_version=1.2-SNAPSHOT -Pjava_version=17
build_gradle fabric fabric 1.19 \
  -Pminecraft_family=1.19 -Pminecraft_version=1.19.4 -Pminecraft_range='>=1.19 <1.20' \
  -Pyarn_mappings=1.19.4+build.2 -Ploader_version=0.14.24 -Pfabric_api_version=0.87.0+1.19.4 \
  -Ploom_version=1.3-SNAPSHOT -Pjava_version=17
build_gradle fabric fabric 1.20 \
  -Pminecraft_family=1.20 -Pminecraft_version=1.20.6 -Pminecraft_range='>=1.20 <1.21' \
  -Pyarn_mappings=1.20.6+build.1 -Ploader_version=0.15.11 -Pfabric_api_version=0.100.8+1.20.6 \
  -Ploom_version=1.7-SNAPSHOT -Pjava_version=21

echo "Forge 1.18 / 1.19 / 1.20.1"
build_gradle forge forge 1.18 \
  -Pminecraft_family=1.18 -Pminecraft_version=1.18.2 -Pminecraft_range='[1.18,1.19)' \
  -Pforge_version=40.2.0 -Pforge_loader_range='[40,)' -Pforge_range='[40,)' -Pjava_version=17
build_gradle forge forge 1.19 \
  -Pminecraft_family=1.19 -Pminecraft_version=1.19.4 -Pminecraft_range='[1.19,1.20)' \
  -Pforge_version=45.2.0 -Pforge_loader_range='[45,)' -Pforge_range='[45,)' -Pjava_version=17
build_gradle forge forge 1.20 \
  -Pminecraft_family=1.20 -Pminecraft_version=1.20.1 -Pminecraft_range='[1.20,1.21)' \
  -Pforge_version=47.3.0 -Pforge_loader_range='[47,)' -Pforge_range='[47,)' -Pjava_version=17

echo "NeoForge 1.20.2-1.20.6"
build_gradle neoforge neoforge 1.20.2-1.20.6 \
  -Pminecraft_family=1.20.2-1.20.6 -Pminecraft_version=1.20.6 -Pminecraft_range='[1.20.2,1.21)' \
  -Pneoforge_version=20.6.119 -Pneoforge_loader_range='[20.6,)' -Pneoforge_range='[20.2,21)' -Pjava_version=21

echo "1.21/26.x entries are skipped until exact loader versions are resolved."
echo "See versions.json and README.md."
[[ "$DRY_RUN" == "0" ]] && find "$DIST" -type f -name '*.jar' -exec sha256sum {} \; > "$DIST/checksums.txt"
