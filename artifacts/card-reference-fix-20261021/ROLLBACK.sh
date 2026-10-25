#!/usr/bin/env bash
set -eu
python "$(dirname "$0")/rollback.py" "${1:?Supply project directory}"
