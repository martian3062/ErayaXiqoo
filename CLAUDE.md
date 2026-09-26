# CLAUDE.md — Tachyon (built on ERAYA) · iQOO Hackathon 2026, Hyderabad

> Project brief for Claude Code. Read this whole file before writing any code.
> Owner: Evolet · Solo entry · Productivity track · 26–27 Sept 2026, The Hive, Hyderabad
> Contact: sandhupardeep300@gmail.com · portfoliov32026.vercel.app · github.com/martian3062

---

## 0. One-line pitch

**Tachyon turns a spoken conversation into a confirmed list of commitments — entirely on the phone.**
Local speech recognition → on-device small language model on the Snapdragon NPU → each commitment shown as a proposal → one tap to commit. No audio, transcript or text ever leaves the handset.

Tagline: **Agents propose. Humans commit.**
Stage line: **Nothing leaves the phone. Nothing is written without a tap.**

---

## 1. Hard rules for Claude Code (non-negotiable)

1. **No network calls in the product.** The only allowed HTTP target is `127.0.0.1` (the fallback local servers in Termux). No cloud APIs, no Firebase, no analytics, no crash reporters, no telemetry SDKs.
2. **The app must work in airplane mode.** Every feature is tested with the radio off. If a feature needs network, it does not ship.
3. **Nothing is written to the task list or calendar without a user tap.** The LLM only produces *proposals*.
4. **Native Android app** (Kotlin + Jetpack Compose). Not a web app, not a PWA.
5. **Scope is frozen:** one screen flow, one output type (commitments), English + one Indian language (Hindi). Do not add features beyond Section 6 unless the core loop is finished and rehearsed.
6. **Never invent SDK APIs.** For GenieX and WhisperKit, read the official docs / sample apps (links in Section 12) and copy the real call signatures. If unsure, stop and ask.
7. Model files are **not** committed to git. They are pushed to the device separately (Section 9).
8. Every engine sits behind an interface so the NPU path and the fallback path are swappable at runtime.

---

## 2. Hackathon constraints that shape the build

| Constraint | Consequence for this codebase |
|---|---|
| 30-hour build, ~55% "Red Light" (phone only, laptop closed as build machine) and ~45% "Green Light" (phone + laptop) | Code must be editable and buildable from the phone: GitHub Actions builds the APK (Section 10), Termux hosts the fallback engines |
| 25% of score is device telemetry (HackTracker): phone usage 15%, Office Kit usage 10% | Transfer files and mirror via Office Kit, do prompt tuning on the phone |
| Rubric: end product 30%, novelty 20%, creative phone use 15%, technical depth 15%, Office Kit 10%, demo 10% | Polish one loop. Show live latency + "running on NPU" badge for technical depth |
| Loaner device: iQOO 15, Snapdragon 8 Elite Gen 5 (verify in Settings → About phone) | Target arm64-v8a, minSdk 30, target/compileSdk 35+ |
| Demo must run on the phone | Final demo = the installed APK in airplane mode |
| Pre-existing code must be disclosed | ERAYA's propose-then-confirm pattern is pre-existing and disclosed. The Tachyon B2 scaffold (Gradle setup, engine interfaces, Tier 2 engines, recorder service, Room, ERAYA validator, basic UI, Termux scripts, CI) was written on 25 Sept 2026, the day **before** the event, and is disclosed too: tag that state `pre-event-scaffold` in git. Everything after that tag is written at the event |

---

## 3. Architecture

```
┌──────────────────────── DEVICE BOUNDARY · iQOO 15 ────────────────────────┐
│                                                                             │
│  Mic ──► RecorderService ──► 16 kHz mono PCM ──► AsrEngine ──► transcript   │
│         (foreground,            (30 s chunks)     │                │        │
│          type=microphone)                         │                ▼        │
│                                                   │        ExtractionAgent  │
│                                  WhisperKit (NPU) │        (ERAYA layer)    │
│                                  or whisper-server│           │             │
│                                                   │           ▼             │
│                                               LlmEngine ◄── prompt+schema   │
│                                  GenieX (NPU) or llama-server (CPU/GPU)     │
│                                                   │                         │
│                                                   ▼                         │
│                              SchemaValidator ──► proposals (PROPOSED)       │
│                                                   │                         │
│                                   ProposalsScreen: ✓ accept / ✗ reject      │
│                                                   │                         │
│                              Room DB (ACCEPTED) ──► Calendar insert intent  │
└─────────────────────────────────────────────────────────────────────────────┘
          No API calls. No telemetry. Airplane mode is the default demo condition.
```

### Engine tiers

| Layer | Tier 1 — primary (NPU) | Tier 2 — fallback (guaranteed) | Tier 3 — last resort |
|---|---|---|---|
| ASR | WhisperKit Android (Qualcomm AI Hub, Argmax) | `whisper-server` (whisper.cpp) in Termux on `127.0.0.1:8082` | Pre-recorded WAV fed to Tier 2 |
| LLM | GenieX Android SDK — `com.qualcomm.qti:geniex-android` (check latest version in the GenieX README) with an AI Hub bundle (Qwen3-4B-Instruct-2507) or GGUF Q4_0 | `llama-server` (llama.cpp) in Termux on `127.0.0.1:8081` | Smaller GGUF (Qwen3.5-2B Q4_0) |

Rule: **offline is the guarantee, NPU is the optimisation.** Build Tier 2 first so the demo can never fail; add Tier 1 on top.

---

## 4. Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.x |
| UI | Jetpack Compose + Material 3 |
| Async | Kotlin Coroutines + Flow |
| DB | Room |
| DI | Manual (single `AppContainer` object) — no Hilt, keep builds fast |
| HTTP (fallback engines only) | OkHttp, restricted to `127.0.0.1` |
| JSON | kotlinx.serialization |
| Audio | `AudioRecord` (16 kHz, mono, PCM 16-bit) |
| Build | Gradle KTS, AGP latest stable, arm64-v8a only |
| CI | GitHub Actions → debug APK artifact |
| minSdk / targetSdk | 30 / 35 (or 36 if the installed SDK has it) |
| Package | `com.evolet.tachyon` |

---

## 5. Repository layout

```
tachyon/
├── CLAUDE.md                        ← this file
├── README.md                        ← short public readme (written on Day 2)
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/xml/network_security_config.xml
│       └── java/com/evolet/tachyon/
│           ├── TachyonApp.kt               ← Application, builds AppContainer
│           ├── AppContainer.kt             ← wires engines, DB, settings
│           ├── MainActivity.kt
│           ├── ui/
│           │   ├── theme/
│           │   ├── RecordScreen.kt         ← big mic button, live transcript, airplane-mode badge
│           │   ├── ProposalsScreen.kt      ← proposal cards with evidence quote, ✓ / ✗
│           │   ├── TasksScreen.kt          ← accepted commitments
│           │   ├── SettingsScreen.kt       ← engine picker (NPU / fallback), model path, language
│           │   └── components/LatencyBadge.kt
│           ├── audio/
│           │   ├── RecorderService.kt      ← foreground service, type=microphone
│           │   ├── PcmChunker.kt           ← 30 s chunks for Whisper
│           │   └── WavWriter.kt
│           ├── asr/
│           │   ├── AsrEngine.kt            ← interface
│           │   ├── WhisperKitAsr.kt        ← Tier 1 (NPU)
│           │   └── WhisperServerAsr.kt     ← Tier 2 (127.0.0.1:8082)
│           ├── llm/
│           │   ├── LlmEngine.kt            ← interface
│           │   ├── GenieXLlm.kt            ← Tier 1 (NPU)
│           │   └── LlamaServerLlm.kt       ← Tier 2 (127.0.0.1:8081)
│           ├── eraya/
│           │   ├── ExtractionAgent.kt      ← transcript → proposals
│           │   ├── Prompts.kt              ← loads prompts from assets
│           │   ├── SchemaValidator.kt      ← parse, repair, validate, retry once
│           │   ├── DeadlineResolver.kt     ← sanity-check deadline_iso vs today
│           │   └── ConfirmationLoop.kt     ← PROPOSED → ACCEPTED / REJECTED
│           ├── data/
│           │   ├── AppDb.kt
│           │   ├── Commitment.kt
│           │   ├── Session.kt
│           │   └── Daos.kt
│           └── calendar/
│               └── CalendarBridge.kt       ← ACTION_INSERT intent, no permission needed
│       └── assets/
│           ├── prompts/extract_system.txt
│           ├── prompts/schema.json
│           └── demo/sample_en_hi.wav       ← backup input (recorded on Day 2)
├── termux/
│   ├── setup.sh                    ← installs packages, builds llama.cpp + whisper.cpp
│   ├── run_servers.sh              ← starts both fallback servers with wake-lock
│   └── test_loop.sh                ← record → transcribe → extract, all via curl
├── prompts/
│   ├── fixtures/                   ← transcripts + expected JSON for quick eval
│   └── eval.py                     ← runs fixtures against llama-server, prints precision/recall
├── demo/
│   └── script_en_hi.md             ← the 60-second scripted conversation
└── .github/workflows/apk.yml
```

---

## 6. Features (MVP scope — frozen)

| # | Feature | Acceptance test |
|---|---|---|
| F1 | Record button starts a foreground mic service; stop ends it | Recording survives screen-off for 60 s |
| F2 | Live transcript: each 30 s chunk is transcribed as it completes | Text appears on screen while still recording |
| F3 | Extraction on stop: transcript → list of proposals | 3/3 commitments found in the demo script |
| F4 | Proposal card shows owner, task, to whom, deadline, **evidence quote**, confidence | Evidence text exists verbatim in the transcript |
| F5 | ✓ accepts (→ Room, status ACCEPTED), ✗ rejects (status REJECTED) | Nothing appears in Tasks without a tap |
| F6 | Accepted item → "Add to calendar" button fires `Intent.ACTION_INSERT` | Calendar app opens pre-filled |
| F7 | Airplane-mode badge (reads connectivity state) + latency badge (ASR ms, LLM ms, engine name) | Badge shows "OFFLINE · NPU" during the demo |
| F8 | Settings: switch ASR/LLM engine between Tier 1 and Tier 2 at runtime | Switching doesn't need an app restart |
| F9 | Hindi / Hinglish support | Hindi line in the script produces an English task with the Hindi evidence quote |

Stretch (only after F1–F9 are rehearsed): Telugu line, streaming LLM output animation, export accepted list as text share.

### 6.1 Integration features (spec: `INTEGRATIONSv2.md`)

Added 26 Sept 2026. Build only after F1–F9 pass in airplane mode; follow the build order and cut line in INTEGRATIONSv2.md §13.

| # | Feature | Hyderabad |
|---|---|---|
| F10 | Handshake: two-phone signed commitments via QR + hash-chained ledger | should |
| F11 | Eyes: whiteboard/notes → commitments via camera OCR | stretch |
| F12 | Me attribution: owner voice-print tags "You" vs other speakers | must |
| F13 | People-aware extraction: persona + people cards, "Tight for you" risk chip | must |
| F14 | Twin drafts: follow-up in the owner's style, share sheet only | should |
| F15 | Learning loop: ✓/✗ + draft edits → preference pairs → JSONL export | should |
| F16 | Voice enrolment (3 min): consent gate, voice-print, clone reference | must (print) · stretch (clone) |
| F17 | Behaviour interview: spoken Q&A → confirmed persona | short version |
| F18 | Recall: voice query over the owner's commitments | stretch |
| F19 | Own-voice reminders | stretch |

---

## 7. Interfaces (write these first)

```kotlin
// asr/AsrEngine.kt
interface AsrEngine {
    val name: String                      // e.g. "WhisperKit·NPU", "whisper.cpp·CPU"
    suspend fun load()
    suspend fun transcribe(pcm16k: ShortArray, languageHint: String? = null): AsrResult
    fun close()
}
data class AsrResult(val text: String, val latencyMs: Long, val language: String?)

// llm/LlmEngine.kt
interface LlmEngine {
    val name: String                      // e.g. "GenieX·NPU·Qwen3-4B", "llama.cpp·CPU·Qwen3.5-2B"
    suspend fun load()
    suspend fun complete(system: String, user: String, jsonSchema: String?, maxTokens: Int = 512): LlmResult
    fun close()
}
data class LlmResult(val text: String, val latencyMs: Long, val tokensPerSec: Double?)
```

- `WhisperServerAsr`: write PCM to WAV in memory → `POST http://127.0.0.1:8082/inference` (multipart `file`, `response_format=json`).
- `LlamaServerLlm`: `POST http://127.0.0.1:8081/completion` with `prompt`, `n_predict`, `temperature: 0.1`, and `json_schema` (llama-server supports schema-constrained output).
- `GenieXLlm` / `WhisperKitAsr`: implement strictly from the official sample apps (`ai-hub-apps/apps/geniex_chat_android`, `WhisperKitAndroid`). If GenieX does not support schema-constrained decoding, rely on `SchemaValidator` (parse → repair → retry once).

---

## 8. The ERAYA extraction contract (frozen)

### 8.1 JSON schema — `assets/prompts/schema.json`

```json
{
  "type": "object",
  "properties": {
    "commitments": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "owner":         { "type": "string" },
          "task":          { "type": "string" },
          "to_whom":       { "type": "string" },
          "deadline_text": { "type": "string" },
          "deadline_iso":  { "type": ["string", "null"] },
          "evidence":      { "type": "string" },
          "confidence":    { "type": "number" }
        },
        "required": ["owner", "task", "deadline_text", "evidence", "confidence"]
      }
    }
  },
  "required": ["commitments"]
}
```

### 8.2 System prompt — `assets/prompts/extract_system.txt`

```
You extract commitments from a meeting transcript.

A commitment is an explicit agreement by a named person to do a specific thing,
optionally for someone, optionally by a time. Ignore opinions, ideas, questions,
hypotheticals ("we could", "maybe"), and past actions that are already done.

The transcript may mix English and Hindi (Devanagari or romanised).
Write owner, task, to_whom and deadline_text in English.
Copy "evidence" EXACTLY from the transcript, in its original language — the shortest
span that proves the commitment.

Today's date is {TODAY} ({WEEKDAY}). Convert relative deadlines ("tomorrow",
"Friday", "kal", "shukravar tak", "end of day") into deadline_iso (YYYY-MM-DD or
YYYY-MM-DDTHH:MM). If no deadline was stated, set deadline_text to "none" and
deadline_iso to null.

confidence is 0.0–1.0: how sure you are this is a real commitment.
If there are no commitments, return {"commitments": []}.
Return JSON only. No prose, no markdown.
```

User message = the raw transcript.

### 8.3 SchemaValidator rules

1. Strip anything before the first `{` and after the last `}`.
2. Parse with kotlinx.serialization (`ignoreUnknownKeys = true`, `isLenient = true`).
3. Drop items whose `evidence` is not found in the transcript (case/whitespace-insensitive match) — anti-hallucination guard. Show dropped count in debug log.
4. Drop items with `confidence < 0.4`.
5. If parsing fails → retry once with the appended instruction `Your last output was invalid JSON. Return only valid JSON matching the schema.`
6. If the retry fails → show "Couldn't extract — tap to retry" (never crash).

### 8.4 Data model

```kotlin
@Entity data class Session(
    @PrimaryKey val id: String,
    val startedAt: Long, val durationSec: Int,
    val transcript: String,
    val asrEngine: String, val llmEngine: String,
    val asrMs: Long, val llmMs: Long
)

@Entity data class Commitment(
    @PrimaryKey val id: String,
    val sessionId: String,
    val owner: String, val task: String, val toWhom: String?,
    val deadlineText: String, val deadlineIso: String?,
    val evidence: String, val confidence: Double,
    val status: Status,                 // PROPOSED, ACCEPTED, REJECTED
    val decidedAt: Long?
)
enum class Status { PROPOSED, ACCEPTED, REJECTED }
```

---

## 9. Android specifics (common gotchas)

### Manifest

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<!-- INTERNET is required ONLY so the app can reach 127.0.0.1 fallback servers -->
<uses-permission android:name="android.permission.INTERNET" />

<application
    android:networkSecurityConfig="@xml/network_security_config"
    android:largeHeap="true" ...>
    <service
        android:name=".audio.RecorderService"
        android:exported="false"
        android:foregroundServiceType="microphone" />
</application>
```

### `res/xml/network_security_config.xml` — cleartext to localhost only

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">127.0.0.1</domain>
        <domain includeSubdomains="false">localhost</domain>
    </domain-config>
</network-security-config>
```

### Other gotchas

| Gotcha | Fix |
|---|---|
| OriginOS kills background processes | Settings → Battery → Tachyon + Termux → no restrictions / allow background activity. Show a one-time in-app hint. |
| Foreground service must start within 5 s of `startForegroundService` | Call `startForeground()` in `onCreate` with the notification |
| Mic permission is runtime | Request RECORD_AUDIO + POST_NOTIFICATIONS on first launch |
| Whisper takes max 30 s per window | `PcmChunker` emits 30 s chunks; transcribe each as it completes |
| Large model files | Store in `getExternalFilesDir("models")` → push with Office Kit / `adb push` to `/sdcard/Android/data/com.evolet.tachyon/files/models/` |
| Model load time | Load both engines once at app start (splash state), never per request |
| Thermal throttling on long runs | Keep LLM `maxTokens` ≤ 512, temperature 0.1 |
| Calendar | Use `Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)` — needs no calendar permission |

---

## 10. Build from the phone — GitHub Actions (Red Light)

`.github/workflows/apk.yml`:

```yaml
name: build-apk
on:
  push:
    branches: [main]
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
      - run: chmod +x ./gradlew && ./gradlew assembleDebug --no-daemon
      - uses: actions/upload-artifact@v4
        with:
          name: tachyon-debug-apk
          path: app/build/outputs/apk/debug/*.apk
```

Phone-side loop (Termux):

```bash
git add -A && git commit -m "wip" && git push
gh run watch                                  # wait for the build
gh run download -n tachyon-debug-apk -D ~/storage/downloads/tachyon
# then open the APK from Files → install
```

---

## 11. Termux fallback engines

### `termux/setup.sh`

```bash
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
echo "Done. Copy models into ~/models (ggml-small.bin, *.gguf)."
```

### `termux/run_servers.sh`

```bash
#!/data/data/com.termux/files/usr/bin/bash
termux-wake-lock
LLM=${LLM:-$HOME/models/qwen3.5-2b-q4_0.gguf}
ASR=${ASR:-$HOME/models/ggml-small.bin}
~/src/whisper.cpp/build/bin/whisper-server -m "$ASR" --host 127.0.0.1 --port 8082 -l auto -t 4 &
~/src/llama.cpp/build/bin/llama-server   -m "$LLM" --host 127.0.0.1 --port 8081 -c 4096 -t 6 &
wait
```

### `termux/test_loop.sh` — proves the whole loop before the app exists

```bash
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
  '{prompt: ("<|im_start|>system\n"+$s+"<|im_end|>\n<|im_start|>user\n"+$u+"<|im_end|>\n<|im_start|>assistant\n"),
    n_predict: 512, temperature: 0.1, json_schema: $sc}' \
| curl -s http://127.0.0.1:8081/completion -H 'Content-Type: application/json' -d @- | jq -r .content | jq .
```

(The chat template above is ChatML for Qwen. For Gemma, use `/v1/chat/completions` instead, which applies the model's own template.)

---

## 12. Setup links (install on laptop)

| Tool | Link |
|---|---|
| Office Kit | pc.vivoglobal.com |
| Android Studio (then SDK Manager → NDK + CMake) | https://developer.android.com/studio |
| Platform-tools (adb) | https://developer.android.com/tools/releases/platform-tools |
| Git | https://git-scm.com/downloads |
| GitHub CLI | https://cli.github.com |
| Python 3.11 | https://www.python.org/downloads/ |
| Qualcomm AI Hub (account + API token) | https://aihub.qualcomm.com |
| Node.js LTS | https://nodejs.org |
| Claude Code | https://docs.claude.com/en/docs/claude-code/overview |
| GenieX (Android SDK + docs) | https://github.com/qualcomm/GenieX |
| AI Hub Apps (geniex_chat_android sample) | https://github.com/qualcomm/ai-hub-apps |
| WhisperKit Android | https://github.com/argmaxinc/WhisperKitAndroid |
| llama.cpp | https://github.com/ggml-org/llama.cpp |
| whisper.cpp | https://github.com/ggml-org/whisper.cpp |
| sherpa-onnx (ASR/VAD backup) | https://github.com/k2-fsa/sherpa-onnx |
| Termux APK | https://github.com/termux/termux-app/releases |
| Termux:API APK | https://github.com/termux/termux-api/releases |
| Whisper ggml models | https://huggingface.co/ggerganov/whisper.cpp/tree/main |
| Qwen3.5-2B GGUF (Q4_0) | https://huggingface.co/unsloth/Qwen3.5-2B-GGUF |
| AI Hub model catalogue (Qwen3-4B-Instruct-2507 bundle) | https://aihub.qualcomm.com/models |

Laptop pip: `pip install qai-hub qai-hub-models huggingface_hub` → `qai-hub configure --api_token <TOKEN>`

Note: the GenieX CLI runs only on Windows ARM64 / Linux ARM64. On an x86 laptop use GenieX only through the Android Gradle dependency.

---

## 13. Build plan — Red / Green

| Block | Light | Goal | Done when |
|---|---|---|---|
| B0 (check-in, 45 min) | 🔴 | Phone setup: About phone, Developer options, USB debugging, Stay awake, battery exemptions, Office Kit connected, Termux + Termux:API installed, models transferred | `termux/setup.sh` running |
| B1 | 🔴 | Termux fallback loop end to end | `test_loop.sh` prints valid JSON with 3 commitments from the demo script |
| B2 | 🟢 | Android skeleton: Compose app, interfaces (Section 7), Tier 2 engines, RecorderService, Room, CI workflow | APK from GitHub Actions records → transcribes → shows proposals, using Tier 2 |
| B3 | 🔴 | Prompt + schema tuning on phone, `prompts/eval.py` fixtures, Hindi lines, latency benchmarks (2B vs 4B, threads) | 3/3 recall on demo script in English + Hindi, LLM < 6 s |
| B4 | 🟢 | Tier 1: GenieXLlm from the sample app, then WhisperKitAsr. Engine switch in Settings. | Latency badge shows NPU engine; same JSON as Tier 2 |
| B5 | 🔴 | UI polish on phone: proposal cards, evidence highlight, accept/reject animation, airplane badge, calendar intent | Full loop in airplane mode, 3 runs in a row without failure |
| B6 | 🟢 | README, record backup demo video, final deck tweaks (latency numbers, architecture) | Video saved on phone + laptop |
| B7 | 🔴 | Rehearsals ×5 in airplane mode, failure drills (kill Termux → restart, NPU off → fallback), pitch practice | Loop < 90 s from "stop" to confirmed list |

If Tier 1 is not stable by the end of B4, **stop working on it**, ship Tier 2, and say so honestly in the pitch.

---

## 14. Demo script — `demo/script_en_hi.md`

Two voices (Evolet + one volunteer), ~60 seconds, three commitments buried in chatter.

```
A: Okay, quick sync on the clinic rollout. Weather's been crazy, by the way.
B: Tell me about it. So where are we on the patient intake form?
A: I'll send you the revised intake form by Friday evening.            ← commitment 1 (A → B, Friday)
B: Great. Maybe we should also think about a WhatsApp reminder someday. ← NOT a commitment (hypothetical)
A: Haan, aur pharmacy wale data ka kya?
B: Main kal tak pharmacy ka stock report bhej dunga.                   ← commitment 2 (B, tomorrow, Hindi)
A: Perfect. And Alex already fixed the printer last week.              ← NOT a commitment (past, done)
B: One more thing — can you book the training room for Monday?
A: Yes, I'll book the training room for Monday morning today itself.   ← commitment 3 (A, today)
```

On stage: accept commitments 1 and 3, **reject** commitment 2 to prove the human is in control.

Expected JSON (fixture for `prompts/fixtures/demo.json`): 3 items; the WhatsApp idea and the printer line must NOT appear.

---

## 15. Stage demo sequence

| Step | What the jury sees |
|---|---|
| 1 | Pull down the notification shade → **Airplane mode ON** on camera |
| 2 | Open Tachyon → badge reads "OFFLINE · NPU" |
| 3 | Tap record → 60-second scripted conversation → live transcript appears in chunks |
| 4 | Tap stop → proposals appear with evidence quotes and a latency badge (e.g. "ASR 2.1 s · LLM 3.4 s · GenieX NPU") |
| 5 | ✓ ✓ ✗ → Tasks screen shows two confirmed items → "Add to calendar" on one |
| 6 | Closing line: "Nothing left the phone. Nothing was written without a tap." |

Backup: if live mic fails, press "Use sample recording" (Settings) → runs the bundled WAV through the same pipeline.

---

## 16. Risk register

| Risk | Likelihood | Mitigation |
|---|---|---|
| GenieX / NPU integration unstable | Medium | Tier 2 built first; runtime switch in Settings |
| Termux killed during demo | Medium | Wake-lock, battery exemption, in-app "restart engine" button that opens Termux via intent |
| Hindi ASR errors | Medium | Clear scripted Hindi; evidence quote + reject button turn errors into a trust demo |
| LLM hallucinated commitment | Low–Medium | Evidence-in-transcript check in SchemaValidator |
| Venue Wi-Fi slow (CI builds, git) | High | Hotspot from own phone; models already transferred via Office Kit |
| Stage noise | Medium | Hold phone close; sample WAV fallback |
| Overheating after long sessions | Low | Close other apps; keep maxTokens low |

---

## 17. Pitch notes (what to say)

- **Problem:** commitments made out loud die when the conversation ends.
- **Why unsolved:** every meeting assistant uploads audio to a server — unusable in clinics, field sites, factory floors, private calls.
- **Solution:** on-device ASR + on-device SLM on the Snapdragon NPU + human confirmation.
- **Technical depth:** schema-constrained extraction, evidence-grounded anti-hallucination check, dual-tier engines with runtime switch, measured latency on device.
- **Phone-first proof:** built largely on the phone (Termux, CI from phone, Office Kit), demoed in airplane mode.
- **Disclosure:** ERAYA's propose-then-confirm pattern is pre-existing. The app scaffold (Tier 2 CPU pipeline, basic UI) was written the day before the event and is tagged `pre-event-scaffold` in git. NPU integration, prompt tuning, Hindi support, UI polish and the demo were built at the event. Show the diff from the tag if asked.
- **Builder credibility:** first-author IEEE ICPC2T 2026 on adaptive local LLM compression under edge constraints; India 1 Hackathon 2025 finalist (zero-connectivity telemedicine); Data Scientist at 4baseCare Precision Health.

---

## 18. Definition of done

- [ ] APK installs from GitHub Actions artifact
- [ ] Full loop works in airplane mode, 5 consecutive runs
- [ ] Demo script: 3/3 commitments, 0 false positives
- [ ] Hindi line extracted correctly with original-language evidence
- [ ] Engine switch works (NPU ↔ fallback) without restart
- [ ] Latency badge shows real numbers
- [ ] Calendar intent opens pre-filled
- [ ] Backup WAV path works
- [ ] Backup demo video recorded
- [ ] README + disclosure line written

---

## 19. How Claude Code should work in this repo

1. Start with Section 7 interfaces and Tier 2 engines — never block on the NPU.
2. Keep each file small and single-purpose; this code is edited on a phone during Red Light.
3. After each feature, give a one-line manual test the user can run on the device.
4. Do not add dependencies without saying why. Prefer AndroidX / Kotlin stdlib.
5. When touching GenieX or WhisperKit, first open the official sample in `ai-hub-apps` / `WhisperKitAndroid` and mirror its setup (Gradle deps, model paths, init calls).
6. Never add code that calls any host other than `127.0.0.1`.
