#!/usr/bin/env bash
# Certifies every remaining world sequentially, waiting for world 1 first.
set -u
cd "$(dirname "$0")/.."
export JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME=/opt/android-sdk

while [ ! -f /tmp/opencode/w1.status ]; do sleep 20; done

for world in $(seq 2 12); do
    echo "=== world $world $(date +%H:%M:%S) ==="
    ./gradlew :leveltool:run --args="generate --world $world" --console=plain > "/tmp/opencode/gen-w$world.log" 2>&1
    ./gradlew :leveltool:run --args="certify --world $world --games 60" --console=plain > "/tmp/opencode/cert-w$world.log" 2>&1
    tail -2 "/tmp/opencode/cert-w$world.log"
done
echo "ALL WORLDS DONE $(date +%H:%M:%S)"
