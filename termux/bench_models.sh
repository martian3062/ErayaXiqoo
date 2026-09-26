#!/data/data/com.termux/files/usr/bin/bash
# Bake-off: for each GGUF given (or all in ~/models), restart llama-server on :8081 and run prompts/bench.py.
# Usage: bash termux/bench_models.sh [model.gguf ...]    Results append to ~/bench_results.jsonl
REPO="$(cd "$(dirname "$0")/.." && pwd)"
MODELS=("$@"); [ ${#MODELS[@]} -eq 0 ] && MODELS=(~/models/*.gguf)
for m in "${MODELS[@]}"; do
  label=$(basename "$m" .gguf)
  pkill -f bin/llama-server; for i in $(seq 1 30); do pgrep -f bin/llama-server >/dev/null || break; sleep 1; done
  pgrep -f bin/llama-server >/dev/null && pkill -9 -f bin/llama-server; sleep 1
  ~/src/llama.cpp/build/bin/llama-server -m "$m" --host 127.0.0.1 --port 8081 \
      -c 12288 -np 3 -t 6 --reasoning-budget 0 > ~/llama_$label.log 2>&1 &
  for i in $(seq 1 60); do curl -sf 127.0.0.1:8081/health >/dev/null && break; sleep 2; done
  curl -sf 127.0.0.1:8081/health >/dev/null || { echo "{\"model\":\"$label\",\"error\":\"failed to load\"}"; continue; }
  python "$REPO/prompts/bench.py" --label "$label" ${STRATS:+--strategies $STRATS} | tee -a ~/bench_results.jsonl
done
