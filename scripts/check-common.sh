#!/usr/bin/env bash
# commonMain must stay platform-free: no android.*/java.* imports or JVM-only calls.
# The app has only an Android target, so the compiler can't check this; this grep does.
set -euo pipefail
cd "$(dirname "$0")/../tvfreedom-android/composeApp/src/commonMain"
pattern='^import (android|java)\.|javaClass|\bSystem\.(getProperty|currentTimeMillis|nanoTime|out|err|exit|lineSeparator)|"\.format\(|sortedMapOf|toSortedMap\(|putIfAbsent|Charsets|synchronized|Thread\.|String\.format\('
if hits=$(grep -rnE "$pattern" --include='*.kt' . | grep -v '^\S*:[0-9]*:\s*\(/\*\|\*\|//\)'); then
  echo "commonMain uses platform-only APIs:"; echo "$hits"; exit 1
fi
echo "commonMain: clean"
