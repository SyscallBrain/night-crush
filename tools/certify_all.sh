#!/usr/bin/env bash
# Full content certification through the installed leveltool binary (no Gradle
# daemon involved, so a stray --stop cannot kill a world mid-run).
set -u
cd "$(dirname "$0")/.."
export JAVA_HOME=/opt/android-studio/jbr
BIN=leveltool/build/install/leveltool/bin/leveltool

for world in $(seq 1 12); do
    dir=$(printf 'content/levels/world-%02d' "$world")
    done_count=$(ls "$dir" 2>/dev/null | wc -l)
    last=$(printf 'content/certificates/level-%04d/hard.json' $((world * 50)))
    if [ "$done_count" -eq 50 ] && [ -f "$last" ]; then
        echo "world $world already certified"
        continue
    fi
    "$BIN" generate --world "$world" >> /tmp/opencode/full-gen.log 2>&1
    "$BIN" certify --world "$world" --games 30 >> /tmp/opencode/full-cert.log 2>&1
    count=$(ls "$dir" 2>/dev/null | wc -l)
    echo "world $world: $count/50 levels ($(date +%H:%M:%S))"
done
"$BIN" daily-pool >> /tmp/opencode/full-cert.log 2>&1
echo "ALL DONE $(date +%H:%M:%S)"
