#!/usr/bin/env sh
set -eu
BASE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec python "$BASE/rollback.py" "${1:?Pass target project root}"
