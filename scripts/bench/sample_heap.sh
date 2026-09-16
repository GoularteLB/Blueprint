#!/usr/bin/env bash
set -euo pipefail

while true; do
  curl -s 'localhost:8080/actuator/metrics/jvm.memory.used?tag=area:heap' \
    | python3 -c 'import sys,json;print(int(json.load(sys.stdin)["measurements"][0]["value"]/1048576),"MB heap")'
  docker stats --no-stream --format '{{.MemUsage}}' backend
  sleep 2
done
