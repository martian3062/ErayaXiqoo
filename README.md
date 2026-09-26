# Tachyon

**Turn a spoken conversation into a confirmed list of commitments, entirely on the phone.**

> Agents propose. Humans commit.
> Nothing leaves the phone. Nothing is written without a tap.

Built for the iQOO Hackathon 2026, Hyderabad (Productivity track), on an iQOO 15 with Snapdragon 8 Elite Gen 5. Built on the ERAYA propose-then-confirm pattern.

---

## Why

Promises made out loud ("I'll send it by Friday") get lost when the meeting ends. Meeting assistants that catch them upload your audio to a server, which rules them out in clinics, on field sites and in private calls.

Tachyon does the whole job on the handset and works in airplane mode.

## How it works

1. 🎙️ **Record.** A foreground service captures 16 kHz audio.
2. 📝 **Transcribe.** Every 30 s chunk is transcribed on-device, so text appears while you talk.
3. 🧠 **Extract.** A small language model on the phone turns the transcript into JSON that must match a fixed schema.
4. 🔍 **Check.** Each item must quote its **evidence** word-for-word from the transcript. Anything the model made up is dropped.
5. ✅ **Confirm.** Every commitment is shown as a *proposal*. Only ✓ saves it, and ✗ discards it.
6. 📅 **Act.** Accepted items go to Tasks and can be added to your calendar with one tap.

Works in English and Hindi (romanised or Devanagari): *"Main kal tak report bhej dunga"* becomes an English task due tomorrow, with the original Hindi line as the evidence.

## Privacy by design

- The only network address the app talks to is `127.0.0.1`. The single HTTP client refuses every other host.
- No cloud APIs, analytics, crash reporters or telemetry.
- Android backup is disabled, so transcripts never reach cloud backup.
- A live badge shows **OFFLINE · NPU/CPU** and warns if the phone goes online.

## Engines

Every engine sits behind one interface and can be switched in Settings without restarting the app.

### Speech to text

- NPU: WhisperKit (Qualcomm AI Hub)
- CPU fallback: whisper.cpp server in Termux

### Language model

- NPU: GenieX with Qwen3-4B-Instruct
- CPU fallback: llama.cpp server in Termux with Qwen3.5-2B Q4_0

Offline is the guarantee, the NPU is the speed-up. The CPU fallback is built first so the demo can't fail.

## Status

- ✅ CPU pipeline code-complete (record → transcribe → extract → confirm)
- ✅ Schema validation + evidence check, 23 unit tests
- ✅ APK built automatically by GitHub Actions
- 🔧 NPU engines (GenieX, WhisperKit): in progress
- 🔧 On-device rehearsals in airplane mode: in progress

---

## Run it on your phone

### 1. Install the app

1. Open **Actions** in this repo, then the latest green **build-apk** run.
2. Download **tachyon-debug-apk** (you need to be signed in to GitHub).
3. Unzip it and open `app-debug.apk`. Allow "install unknown apps" if Android asks.

On first launch, allow the **microphone** and **notifications**.

### 2. Start the on-device engines (Termux)

Install **Termux** and **Termux:API** from their GitHub releases pages, not from the Play Store. Then run in Termux:

```bash
pkg install -y git
git clone https://github.com/martian3062/ErayaXiqoo
bash ErayaXiqoo/termux/setup.sh
```

`setup.sh` builds llama.cpp and whisper.cpp on the phone, which takes a while. Then download the two models:

```bash
cd ~/models
wget -O ggml-small.bin \
 https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin
wget -O qwen3.5-2b-q4_0.gguf \
 https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/main/Qwen3.5-2B-Q4_0.gguf
```

Start both servers and leave Termux running:

```bash
bash ~/ErayaXiqoo/termux/run_servers.sh
```

### 3. Stop the phone killing it

Settings → Battery → set **Tachyon** and **Termux** to *no restrictions*. OriginOS closes background apps aggressively. The app's Settings screen has a shortcut.

### 4. Use it

1. Turn on **airplane mode** (optional, but that's the point).
2. Open Tachyon. Both engines should show ● ready.
3. Tap 🎙️ and have your conversation, then tap ⏹.
4. Review the proposals: ✓ to keep, ✗ to drop.
5. **Tasks** → *Add to calendar*.

No mic handy? Use **Settings → Use sample recording**.

### Troubleshooting

- **"✕ llama-server not ready"**: the Termux servers aren't running. Tap *Open Termux*, run `run_servers.sh`, then *Retry*.
- **Nothing transcribed**: hold the phone closer, or switch the language hint in Settings.
- **App stops when the screen is off**: redo step 3.

---

## Tune it from the phone

Test the prompt against the saved example conversations (the servers must be running):

```bash
python ~/ErayaXiqoo/prompts/eval.py --show
```

It prints precision and recall for the conversations in `prompts/fixtures/`.

To try a new prompt **without rebuilding the app**, push it to:

```text
/sdcard/Android/data/com.evolet.tachyon/
  files/prompts/extract_system.txt
```

(Use adb or Office Kit; Termux can't write there.) The next extraction picks it up.

## Build from the phone

Every push to `main` builds a new APK:

```bash
git commit -am "wip" && git push
gh run watch
gh run download -n tachyon-debug-apk \
  -D ~/storage/downloads/tachyon
```

<details>
<summary>Build on a laptop instead</summary>

Needs JDK 17 and the Android SDK (platform 37).

```bash
./gradlew assembleDebug testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

</details>

---

## Tech

Kotlin · Jetpack Compose · Material 3 · Coroutines/Flow · Room · kotlinx.serialization · OkHttp (localhost only)

AGP 9.4 · Kotlin 2.4 · minSdk 30 · targetSdk 35 · arm64-v8a

```text
app/src/main/java/com/evolet/tachyon/
  asr/       speech engines
  llm/       language-model engines
  eraya/     extraction, validation,
             deadlines, confirmation
  audio/     recorder service, chunking
  session/   the pipeline
  ui/        Compose screens
termux/      on-device server scripts
prompts/     eval harness + fixtures
```

## Disclosure

- The **ERAYA** propose-then-confirm pattern is pre-existing work.
- The app scaffold (CPU pipeline, basic UI) was written on 25 Sept 2026, the day **before** the event. It is tagged [`pre-event-scaffold`](https://github.com/martian3062/ErayaXiqoo/tree/pre-event-scaffold).
- Everything after that tag was written at the event. [Compare the changes](https://github.com/martian3062/ErayaXiqoo/compare/pre-event-scaffold...main).

---

Made by **Evolet** on and for a phone.
