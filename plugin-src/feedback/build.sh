#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
HOST_JAR="../../target/swust-skin.jar"
if [ ! -f "$HOST_JAR" ]; then
  echo "未找到 target/swust-skin.jar，请先构建宿主：mvn -DskipTests package" >&2
  exit 1
fi
OUT="../../plugins/feedback"
mkdir -p "$OUT"
rm -rf "$OUT/cn"
javac --release 21 -encoding UTF-8 -cp "$HOST_JAR" -d "$OUT" src/cn/edu/swust/mc/skin/plugins/feedback/*.java
cp plugin.json "$OUT/plugin.json"
cp client.mjs "$OUT/client.mjs"
(cd "$OUT" && jar --create --file "$OLDPWD/feedback.jar" plugin.json client.mjs cn)
echo "已构建插件：plugins/feedback 与 plugin-src/feedback/feedback.jar"
