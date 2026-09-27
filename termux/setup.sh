#!/data/data/com.termux/files/usr/bin/bash
set -e
pkg update -y && pkg upgrade -y
pkg install -y git gh cmake clang make python nodejs-lts ffmpeg termux-api wget jq
termux-setup-storage
mkdir -p ~/src ~/models
cd ~/src
[ -d llama.cpp ]   || git clone --depth 1 https://github.com/ggml-org/llama.cpp
[ -d whisper.cpp ] || git clone --depth 1 https://github.com/ggml-org/whisper.cpp
cd ~/src/llama.cpp   && cmake -B build -DCMAKE_BUILD_TYPE=Release && cmake --build build -j8 --target llama-server
cd ~/src/whisper.cpp && cmake -B build -DCMAKE_BUILD_TYPE=Release && cmake --build build -j8 --target whisper-server
echo "Done. Copy models into ~/models (ggml-base-q5_1.bin, *.gguf)."
