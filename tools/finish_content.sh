#!/usr/bin/env bash
# Final content pass: re-certify every world with the corrected revision order,
# then fill the gaps with varied families until every world has 50 levels.
set -u
cd "$(dirname "$0")/.."
bash tools/recertify_all.sh
bash tools/fill_deep.sh
echo "FINISH CONTENT DONE $(date +%H:%M:%S)"
