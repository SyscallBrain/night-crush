#!/usr/bin/env bash
# Full content pipeline: certify every world, then fill the gaps, then the pool.
set -u
cd "$(dirname "$0")/.."
bash tools/certify_all.sh
bash tools/fill_gaps.sh
echo "PIPELINE DONE $(date +%H:%M:%S)"
