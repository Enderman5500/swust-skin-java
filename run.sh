#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
if [ ! -f target/swust-skin.jar ]; then
  if command -v mvn >/dev/null 2>&1; then
    mvn -DskipTests package
  elif [ -x ../.tools/apache-maven-3.9.11/bin/mvn ]; then
    ../.tools/apache-maven-3.9.11/bin/mvn -DskipTests package
  else
    echo "未找到 Maven。请安装 Maven 3.9+ 并加入 PATH。" >&2
    exit 1
  fi
fi
exec java --enable-native-access=ALL-UNNAMED -jar target/swust-skin.jar
