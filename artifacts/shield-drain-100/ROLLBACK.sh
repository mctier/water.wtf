#!/usr/bin/env bash
set -eu
cp "$(dirname "$0")/BASELINE.java" "${1:?Supply AutoMace.java path}"
