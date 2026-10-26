#!/usr/bin/env bash
# Re-certifies every published level with the current calibration rules:
# drops stale candidates for levels that are already published, then certifies
# each world (adaptive calibration + solve + fresh certificates).
set -u
cd "$(dirname "$0")/.."
export JAVA_HOME=/opt/android-studio/jbr
BIN=leveltool/build/install/leveltool/bin/leveltool

for world in $(seq 1 12); do
    first=$((world * 50 - 49))
    last=$((world * 50))
    for ordinal in $(seq "$first" "$last"); do
        published=$(printf 'content/levels/world-%02d/level-%04d.json' "$world" "$ordinal")
        candidate=$(printf 'content/candidates/world-%02d/level-%04d.json' "$world" "$ordinal")
        if [ -f "$published" ] && [ -f "$candidate" ]; then
            rm -f "$candidate"
        fi
    done
    "$BIN" certify --world "$world" --games 30 >> /tmp/opencode/recertify.log 2>&1
    echo "world $world re-certified $(date +%H:%M:%S)"
done
"$BIN" daily-pool >> /tmp/opencode/recertify.log 2>&1
echo "RECERTIFY DONE $(date +%H:%M:%S)"
