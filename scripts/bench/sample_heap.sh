#!/usr/bin/env bash
set -euo pipefail

CONTAINER=$(docker compose ps -q backend)

while true; do
  curl -s 'localhost:8080/actuator/metrics/jvm.memory.used?tag=area:heap' \
    | python -c 'import sys,json;print(int(json.load(sys.stdin)["measurements"][0]["value"]/1048576),"MB heap")'
  docker stats --no-stream --format '{{.MemUsage}}' "$CONTAINER"
  sleep 2
done
