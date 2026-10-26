#!/usr/bin/env bash
# Fills the gaps left by rejected levels: regenerate the missing ordinals with a
# fresh seed and certify the world once.
set -u
cd "$(dirname "$0")/.."
export JAVA_HOME=/opt/android-studio/jbr
BIN=leveltool/build/install/leveltool/bin/leveltool

for round in 1 2 3 4; do
    missing_total=0
    for world in $(seq 1 12); do
        dir=$(printf 'content/levels/world-%02d' "$world")
        count=$(ls "$dir" 2>/dev/null | wc -l)
        if [ "$count" -eq 50 ]; then continue; fi
        first=$((world * 50 - 49))
        last=$((world * 50))
        generated=0
        for ordinal in $(seq "$first" "$last"); do
            path=$(printf 'content/levels/world-%02d/level-%04d.json' "$world" "$ordinal")
            if [ -f "$path" ]; then continue; fi
            missing_total=$((missing_total + 1))
            seed=$((20261013 + round * 104729 + ordinal * 31))
            "$BIN" generate --world "$world" --from "$ordinal" --to "$ordinal" --seed "$seed" >> /tmp/opencode/fill.log 2>&1
            generated=$((generated + 1))
        done
        if [ "$generated" -gt 0 ]; then
            "$BIN" certify --world "$world" --games 60 >> /tmp/opencode/fill.log 2>&1
        fi
    done
    echo "round $round: $missing_total missing at start"
    if [ "$missing_total" -eq 0 ]; then break; fi
done
"$BIN" daily-pool >> /tmp/opencode/fill.log 2>&1
echo "FILL DONE $(date +%H:%M:%S)"
