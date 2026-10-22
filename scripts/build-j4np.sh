#!/bin/sh
# Build the pinned upstream modules required by java-paw.
set -eu
project_dir=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
j4np_dir=${J4NP_DIR:-"$project_dir/.deps/j4np"}
j4np_repository=${J4NP_REPOSITORY:-https://github.com/gavalian/j4np.git}
j4np_ref=${J4NP_REF:-$(cat "$project_dir/scripts/j4np.ref")}
j4np_version=${J4NP_VERSION:-1.2.1}

# An explicitly supplied source directory is left on its current revision.
if [ -z "${J4NP_DIR:-}" ]; then
  if [ ! -d "$j4np_dir/.git" ]; then
    mkdir -p "$(dirname "$j4np_dir")"
    git clone --no-checkout "$j4np_repository" "$j4np_dir"
  fi
  if ! git -C "$j4np_dir" cat-file -e "$j4np_ref^{commit}" 2>/dev/null; then
    git -C "$j4np_dir" fetch origin "$j4np_ref"
  fi
  git -C "$j4np_dir" checkout --detach "$j4np_ref"
fi
if [ ! -f "$j4np_dir/pom.xml" ]; then
  echo "Missing j4np sources: $j4np_dir" >&2
  exit 1
fi
mvn --batch-mode --no-transfer-progress -f "$j4np_dir/pom.xml" \
  -pl modules/j4np-ui -am clean install \
  "-Drevision=$j4np_version" -DskipTests "$@"
