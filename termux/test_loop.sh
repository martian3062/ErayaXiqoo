#!/data/data/com.termux/files/usr/bin/bash
set -e
termux-microphone-record -f /sdcard/t.m4a -l 60 && sleep 61
ffmpeg -y -i /sdcard/t.m4a -ar 16000 -ac 1 /sdcard/t.wav
TX=$(curl -s -F file=@/sdcard/t.wav -F response_format=json http://127.0.0.1:8082/inference | jq -r .text)
echo "TRANSCRIPT: $TX"
PROMPTS="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/prompts"   # works wherever the repo is cloned
SYS=$(sed "s/{TODAY}/$(date +%F)/; s/{WEEKDAY}/$(date +%A)/" "$PROMPTS/extract_system.txt")
SCHEMA=$(cat "$PROMPTS/schema.json")
jq -n --arg s "$SYS" --arg u "$TX" --argjson sc "$SCHEMA" \
  '{prompt: ("<|im_start|>system\n"+$s+"<|im_end|>\n<|im_start|>user\n"+$u+"<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n"),
    n_predict: 512, temperature: 0.1, json_schema: $sc}' \
| curl -s http://127.0.0.1:8081/completion -H 'Content-Type: application/json' -d @- | jq -r .content | jq .
