#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ "${1:-}" = "--with-j4np" ]; then
  ./scripts/build-j4np.sh
  shift
fi
if [ "$#" -eq 0 ]; then
  set -- package
fi
exec mvn "-Dj4np.version=${J4NP_VERSION:-1.2.1}" "$@"
