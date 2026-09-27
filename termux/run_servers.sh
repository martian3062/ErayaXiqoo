#!/data/data/com.termux/files/usr/bin/bash
termux-wake-lock
LLM=${LLM:-$HOME/models/qwen3.5-2b-q4_0.gguf}
ASR=${ASR:-$HOME/models/ggml-base-q5_1.bin}
# The app ignores timestamps. Greedy best-of 1 plus the quantized Base model keeps
# short live chunks responsive while retaining multilingual English/Hindi support.
~/src/whisper.cpp/build/bin/whisper-server -m "$ASR" --host 127.0.0.1 --port 8082 -l auto -t 8 -nt -bo 1 -sns &
~/src/llama.cpp/build/bin/llama-server   -m "$LLM" --host 127.0.0.1 --port 8081 -c 4096 -t 6 &
wait
