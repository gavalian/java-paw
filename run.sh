#!/bin/sh
set -eu
app_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
if [ ! -f "$app_dir/target/paw-java-0.1.0.jar" ]; then
  echo 'Build first: ./build.sh --with-j4np' >&2
  exit 1
fi
exec java -jar "$app_dir/target/paw-java-0.1.0.jar" "$@"
