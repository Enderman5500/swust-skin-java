#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
HOST_JAR="../../target/swust-skin.jar"
if [ ! -f "$HOST_JAR" ]; then
  echo "未找到 target/swust-skin.jar，请先构建宿主：mvn -DskipTests package" >&2
  exit 1
fi
OUT="../../plugins/activity"
mkdir -p "$OUT"
rm -rf "$OUT/cn"
javac --release 21 -encoding UTF-8 -cp "$HOST_JAR" -d "$OUT" src/cn/edu/swust/mc/skin/plugins/activity/*.java
cp plugin.json "$OUT/plugin.json"
cp client.mjs "$OUT/client.mjs"
(cd "$OUT" && jar --create --file "$OLDPWD/activity.jar" plugin.json client.mjs cn)
echo "已构建插件：plugins/activity 与 plugin-src/activity/activity.jar"
