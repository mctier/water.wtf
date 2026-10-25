#!/usr/bin/env bash
set -eu
root="${1:?Supply project root}"
base="$(cd "$(dirname "$0")" && pwd)/baseline"
for f in "$base"/src/client/java/com/utilitymod/gui/CardGui.java "$base"/src/client/java/com/utilitymod/gui/GuiConfig.java "$base"/src/client/java/com/utilitymod/echo/render/GlassRenderer.java; do
  rel="${f#$base/}"; mkdir -p "$root/$(dirname "$rel")"; cp "$f" "$root/$rel"
done
rm -f "$root/src/client/java/com/utilitymod/gui/CardGuiMotion.java" "$root/src/client/java/com/utilitymod/gui/CardGuiStyle.java"
echo "ROLLBACK restored CardGui, GuiConfig and GlassRenderer; removed CardGuiMotion and CardGuiStyle"
