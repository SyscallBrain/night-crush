#!/usr/bin/env bash
# Full content regeneration (engine changes or generator changes): wipes the
# published levels and certificates, generates and certifies every world, then
# rebuilds rejected ordinals with fresh board seeds (and, late, fresh plans).
# Takes hours; run it in the background and follow $LOG.
set -u
cd "$(dirname "$0")/.."
export JAVA_HOME=/opt/android-studio/jbr
BIN=leveltool/build/install/leveltool/bin/leveltool
LOG=${LOG:-build/regenerate.log}
GAMES=${GAMES:-30}
mkdir -p "$(dirname "$LOG")"

if [ "${KEEP:-0}" != "1" ]; then
    rm -rf content/levels content/certificates content/candidates
fi

for world in $(seq 1 12); do
    dir=$(printf 'content/levels/world-%02d' "$world")
    if [ "$(ls "$dir" 2>/dev/null | wc -l)" -eq 50 ]; then continue; fi
    "$BIN" generate --world "$world" >> "$LOG" 2>&1
    "$BIN" certify --world "$world" --games "$GAMES" >> "$LOG" 2>&1
    echo "world $world: $(ls "$dir" 2>/dev/null | wc -l)/50 ($(date +%H:%M:%S))"
done

for round in 1 2 3 4 5; do
    missing_total=0
    for world in $(seq 1 12); do
        dir=$(printf 'content/levels/world-%02d' "$world")
        if [ "$(ls "$dir" 2>/dev/null | wc -l)" -eq 50 ]; then continue; fi
        for ordinal in $(seq $((world * 50 - 49)) $((world * 50))); do
            path=$(printf '%s/level-%04d.json' "$dir" "$ordinal")
            if [ -f "$path" ]; then continue; fi
            missing_total=$((missing_total + 1))
            seed=$((20261013 + round * 104729 + ordinal * 31))
            plan=20261013
            # Late rounds also move the level to another family of its world.
            if [ "$round" -ge 4 ]; then plan=$seed; fi
            "$BIN" generate --world "$world" --from "$ordinal" --to "$ordinal" --seed "$seed" --plan-seed "$plan" >> "$LOG" 2>&1
        done
        deep=""
        if [ "$round" -ge 3 ]; then deep="--deep"; fi
        "$BIN" certify --world "$world" --games "$GAMES" $deep >> "$LOG" 2>&1
    done
    echo "gap round $round: $missing_total missing at start ($(date +%H:%M:%S))"
    if [ "$missing_total" -eq 0 ]; then break; fi
done
"$BIN" daily-pool >> "$LOG" 2>&1
echo "REGENERATE DONE $(date +%H:%M:%S)"
