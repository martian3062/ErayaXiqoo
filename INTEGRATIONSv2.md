# INTEGRATIONS.md — Tachyon × Self (ERAYA agents + personal twin layer)

> Companion spec to `CLAUDE.md`. Claude Code: read `CLAUDE.md` first, then this file.
> Everything here extends Tachyon (package `com.evolet.tachyon`, repo github.com/martian3062/ErayaXiqoo).
> Owner: Evolet · iQOO Hackathon 2026 Hyderabad → Grand Finale Bengaluru (9–11 Oct 2026)

---

## 0. The idea in one paragraph

Tachyon already turns a conversation into confirmed commitments on the phone. **Tachyon × Self** adds a personal layer so the phone *knows its owner*: it recognises the owner's voice, understands who the other people are, drafts follow-ups in the owner's own writing style, can speak reminders in the owner's own (cloned) voice, and learns from every ✓/✗ tap. Personalization is bootstrapped in **two on-phone onboarding sessions**: a **3-minute voice enrolment** (voice-print + voice clone) and a **30-minute behaviour interview** (spoken Q&A → a confirmed personal profile). Everything runs on the handset and works in airplane mode.

**Design law carried through every feature: agents propose, humans commit.** The twin proposes traits, drafts, reminders and voice output; the owner confirms each one. Nothing is sent, saved as a trait, or spoken to another person without a tap.

Pitch line: *"Other assistants transcribe. Tachyon knows it was you who promised, knows who you promised, writes the follow-up the way you would, reminds you in your own voice — and learns from every tap. None of it leaves the phone."*

---

## 1. Hard rules (extend CLAUDE.md §1)

1. **No network.** Only `127.0.0.1` for the Termux fallback servers. No cloud TTS (no ElevenLabs), no cloud STT, no analytics.
2. **Owner-only voice cloning.** A voice model may be created **only** from the device owner's own voice, after a spoken consent phrase that the ASR verifies (§6.3). No UI path exists to clone anyone else's voice, and no import of third-party audio for cloning.
3. **Every synthetic voice output is labelled.** On-screen "AI voice" chip every time it plays; exported voice notes carry a spoken disclosure prefix and a metadata tag (§6.6). Default: voice notes to other people are **off**.
4. **Every inferred trait needs evidence + confirmation.** The behaviour interview produces *proposed* traits, each with the quote it came from. Only confirmed traits enter `persona.json`. No psychological, medical, or diagnostic labels — ever (§7.6).
5. **Nothing is sent automatically.** Drafts open the Android share sheet; the owner sends.
6. **Guardian strips personal data** from anything that leaves the app boundary (Handshake QR, share sheet, exports) according to trust tiers (§9).
7. **Forget-me is one tap.** Settings → *Delete my twin* wipes voice-print, voice model, persona, people, preferences, ledger keys (§10).
8. **Never invent SDK APIs.** For sherpa-onnx, GenieX, ML Kit, CameraX, llama-server: open the official docs/samples and mirror them. If unsure, stop and ask.
9. **Loaner-device rule.** At the hackathon the phone is a loaner: use a **demo people list** and wipe everything before return (§10.3).

---

## 2. Feature map

| ID | Feature | Group | Hyderabad (30 h) | Finale (48 h) |
|---|---|---|---|---|
| F1–F9 | Core loop (record → transcribe → extract → confirm → calendar) | Core | ✅ must | ✅ |
| F10 | **Handshake** — two-phone signed commitments via QR + hash-chained ledger | Trust | ✅ should | ✅ + Nearby |
| F11 | **Eyes** — whiteboard/notes → commitments via camera (OCR, VLM stretch) | Multimodal | ⚪ if time | ✅ |
| F12 | **Me attribution** — owner voice-print tags "You" vs Speaker B/C | Twin | ✅ must | ✅ |
| F13 | **People-aware extraction** — persona + people cards in the prompt, personal deadline risk | Twin | ✅ must | ✅ |
| F14 | **Twin drafts** — follow-up message in owner's style (LoRA or persona few-shot) | Twin | ✅ should | ✅ |
| F15 | **Learning loop** — ✓/✗ + draft edits → preference pairs → JSONL export for DPO | Twin | ✅ should | ✅ |
| F16 | **Voice enrolment (3 min)** — voice-print + voice-clone reference/profile | Onboarding | ✅ voice-print · ⚪ clone | ✅ clone |
| F17 | **Behaviour interview (30 min, demo = 5 min)** — spoken Q&A → confirmed persona | Onboarding | ✅ short version | ✅ full |
| F18 | **Recall** — "What did I promise Alex?" voice query over local commitments | Twin | ⚪ if time | ✅ |
| F19 | **Own-voice reminders** — deadline reminders spoken in the owner's cloned voice | Twin | ⚪ if time | ✅ |

Legend: ✅ must/should · ⚪ stretch. Cut from the bottom of the Hyderabad column first.

Add F10–F19 to `CLAUDE.md` §6 before implementing, or Claude Code's scope rule blocks it.

---

## 3. On-device agent architecture (ERAYA, Kotlin edition)

The Django/NATS ERAYA stack does **not** run on the phone. This is a lightweight Kotlin re-implementation of the same archetypes: coroutines + a typed in-process event bus.

```
                        ┌──────────── DEVICE BOUNDARY ────────────┐
 mic / camera ─► PERCEIVER ─► PLANNER ─► GUARDIAN ─► UI (propose) ─► owner tap
                    │            │  ▲         │                         │
                    │            │  │         ▼                         ▼
                    │            │  TWIN   (strip/tier)            LEARNER
                    │            │  ▲                                   │
                    │            ▼  │                                   ▼
                    │          RECALL ◄──── Room + vector table ◄── confirmed data
                    ▼
               RECOVERER (engine health, tier fallback, retries)
                        └──────────────────────────────────────────┘
```

| Agent | Responsibility | Inputs → outputs | Key tech |
|---|---|---|---|
| **Perceiver** | Audio/image in, text + speaker labels out | PCM / Bitmap → `Utterance(text, speaker, lang, t0, t1)` | AsrEngine, SpeakerId (sherpa-onnx), ML Kit OCR |
| **Planner** | Commitments from utterances, with personal context | `Transcript` + persona/people cards → `Proposal[]` | ExtractionAgent + SchemaValidator |
| **Twin** | Owner-style drafts, trait proposals, reminder text | `Commitment` + person → `Draft`; interview answers → `TraitProposal[]` | LlmEngine with style LoRA / persona few-shot |
| **Recall** | Question answering over the owner's own records | query → ranked commitments + answer | embedding model + local vector table |
| **Guardian** | Trust tiers, redaction, consent checks, signing | any outbound payload → allowed/stripped payload | TrustPolicy, Keystore signer |
| **Learner** | Turns taps and edits into training data | UI events → `PreferencePair` → JSONL | Room |
| **Recoverer** | Keeps the pipeline alive | engine health → switch tier / retry / user hint | EngineHealth monitor |

### 3.1 Bus contract

```kotlin
sealed interface AgentEvent {
    data class UtteranceReady(val u: Utterance) : AgentEvent
    data class TranscriptClosed(val sessionId: String) : AgentEvent
    data class ProposalsReady(val sessionId: String, val items: List<Proposal>) : AgentEvent
    data class OwnerDecision(val proposalId: String, val decision: Decision, val editedText: String?) : AgentEvent
    data class DraftRequested(val commitmentId: String) : AgentEvent
    data class DraftReady(val commitmentId: String, val draft: Draft) : AgentEvent
    data class TraitsProposed(val interviewId: String, val traits: List<TraitProposal>) : AgentEvent
    data class EngineDegraded(val engine: String, val reason: String) : AgentEvent
}

class AgentBus { val events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 64) }
```

Each agent is a class with `fun start(scope: CoroutineScope)` that collects the events it cares about. Keep each agent file under ~200 lines — it is edited on a phone during Red Light.

### 3.2 Package layout additions

```
app/src/main/java/com/evolet/tachyon/
├── agents/
│   ├── AgentBus.kt
│   ├── Perceiver.kt
│   ├── Planner.kt
│   ├── TwinAgent.kt
│   ├── RecallAgent.kt
│   ├── Guardian.kt
│   ├── Learner.kt
│   └── Recoverer.kt
├── twin/
│   ├── PersonaStore.kt          ← persona.json / people.json load+save
│   ├── SpeakerId.kt             ← sherpa-onnx speaker embeddings + matching
│   ├── VoiceEnrolment.kt        ← 3-min enrolment session logic
│   ├── VoiceCloneEngine.kt      ← interface + tiers (§6)
│   ├── InterviewEngine.kt       ← 30-min behaviour interview (§7)
│   ├── QuestionBank.kt          ← loads assets/twin/questions.json
│   └── TraitReview.kt           ← propose → confirm logic
├── trust/
│   ├── TrustPolicy.kt           ← tiers + stripping rules
│   ├── KeystoreSigner.kt        ← EC P-256 sign/verify
│   └── Ledger.kt                ← hash chain (Handshake)
├── handshake/                   ← F10 screens (QR show / scan)
├── eyes/                        ← F11 camera + OCR
└── ui/onboarding/
    ├── VoiceEnrolmentScreen.kt
    ├── InterviewScreen.kt
    └── TraitReviewScreen.kt
assets/twin/
├── enrolment_script.json        ← §6.2
├── questions.json               ← §7.3
├── persona.schema.json          ← §8.1
└── people.demo.json             ← §8.2 (fictional demo contacts)
```

---

## 4. Model budget on the iQOO 15

| Model | Purpose | Approx. size | Runs where |
|---|---|---|---|
| Whisper small (multilingual) | ASR | ~250–500 MB | Termux whisper-server (Tier 2) / WhisperKit NPU (Tier 1) |
| Qwen3-4B-Instruct Q4 GGUF | Extraction + drafts + interview summarisation | ~2.5 GB | llama-server (Tier 2) / GenieX NPU (Tier 1) |
| Owner style LoRA (GGUF adapter, Qwen3-4B base) | Twin drafts in owner style | ~50–150 MB | llama-server `--lora` |
| Speaker embedding model (sherpa-onnx, e.g. 3D-Speaker / WeSpeaker family) | Voice-print, "me" attribution | ~25–30 MB | in-app (sherpa-onnx AAR) |
| Silero VAD | Trim silence, end-of-answer detection in interview | ~2 MB | in-app |
| Small multilingual embedding model (e.g. multilingual-e5-small class, ONNX or GGUF) | Recall (F18) | ~100–150 MB | in-app ONNX Runtime or llama-server `--embedding` second instance |
| TTS model (tiered, §6.4) | Voice clone output | 60–400 MB | in-app sherpa-onnx TTS |
| ML Kit Text Recognition v2 (Latin + Devanagari, bundled) | Eyes OCR | ~30 MB | in-app |

**Important:** the style LoRA must match its base model. If the twin's adapter was trained on Qwen3-4B, the extraction base in Termux must be Qwen3-4B (not Qwen3.5-2B). Update `run_servers.sh` and the README when switching.

---

## 5. Twin core features (F12–F15)

### 5.1 F12 — "Me" attribution (voice-print)

| Item | Spec |
|---|---|
| Enrolment | Taken from the 3-min voice enrolment (§6). Compute embeddings over 3–5 s windows of voiced audio, average → `ownerVoiceprint` (float array, L2-normalised). |
| Matching | For each ASR segment ≥ 1.5 s, extract an embedding; cosine similarity vs `ownerVoiceprint`. `≥ threshold` → `speaker = USER`, else cluster other speakers greedily (new cluster if similarity to all existing < threshold) → `SPEAKER_B`, `SPEAKER_C`… |
| Threshold | Start at 0.55; tune on-device with 10 owner clips + 10 other-voice clips; store in Settings. Unit-test the decision function with fixed vectors. |
| UI | Transcript shows coloured speaker chips; tap a chip → rename ("Alex") → updates all segments in the session. |
| Prompt | Transcript fed to the Planner as `[You]: …` / `[Alex]: …` lines. |
| Tech | sherpa-onnx Android speaker embedding extractor + manager. Follow the sherpa-onnx speaker-identification Android example exactly. |
| Tests | Owner speaks 5 lines → ≥ 4 tagged USER; volunteer speaks 5 lines → ≥ 4 tagged not-USER. |

### 5.2 F13 — People-aware extraction

- Planner prompt gains two blocks, prepended to the existing system prompt:
  - `OWNER PROFILE` — confirmed fields from `persona.json` (name, languages, role, habits relevant to commitments).
  - `PEOPLE` — compact list from `people.json`: `id | name | aliases | relation | register`.
- Schema additions (keep old fields):

```json
"owner_is_user": { "type": "boolean" },
"owner_person_id": { "type": ["string", "null"] },
"to_person_id": { "type": ["string", "null"] },
"risk_note": { "type": ["string", "null"] }
```

- **Personal deadline risk** (deterministic, not LLM): after extraction, `RiskScorer` looks up the owner's history of ACCEPTED commitments with similar task category (simple keyword buckets: report / send / book / call / review) and computes median days-late. If the new deadline is shorter than the historical median delivery time → amber chip **"Tight for you"** with the reason in one line. With no history, use the owner's self-reported habit from the interview (e.g. "I usually need 2 days for reports").
- Validator rule: `to_person_id` must exist in `people.json` or be null.

### 5.3 F14 — Twin drafts

| Item | Spec |
|---|---|
| Trigger | "Draft follow-up" button on an ACCEPTED commitment card. |
| Input | commitment, target person (register), owner persona style rules, 2–3 style examples from `persona.json.style_examples`. |
| Output | `Draft(channel = WHATSAPP/EMAIL/SMS, language, text)` — max 60 words, matches register (formal / neutral / Hinglish casual / family). |
| Engine A (preferred) | llama-server with the owner's style LoRA: start server with `--lora style.gguf --lora-init-without-apply`; set adapter scale per request (**verify the exact request field / `/lora-adapters` endpoint in the llama.cpp server README before coding**). Extraction runs with scale 0.0, drafts with 1.0. |
| Engine B (fallback) | Same base model, no LoRA, persona few-shot prompt. |
| Delivery | `Intent.ACTION_SEND` share sheet with the text. Never send automatically. |
| Learner hook | If the owner edits the draft before sharing → store `PreferencePair(prompt, chosen = edited, rejected = original)`. |
| Stage moment | Same commitment, two recipients → formal draft for manager, Hinglish draft for a friend. |

### 5.4 F15 — Learning loop

```kotlin
@Entity data class PreferencePair(
    @PrimaryKey val id: String,
    val kind: Kind,             // PROPOSAL_DECISION, DRAFT_EDIT, TRAIT_DECISION
    val prompt: String,         // context shown to the model
    val chosen: String,         // accepted / edited / confirmed version
    val rejected: String?,      // rejected / original / discarded version
    val createdAt: Long
)
enum class Kind { PROPOSAL_DECISION, DRAFT_EDIT, TRAIT_DECISION }
```

- Settings → **Export for twin training** → writes `tachyon_prefs_YYYYMMDD.jsonl` to Downloads (TRL DPO format: `{"prompt","chosen","rejected"}`), Guardian-stripped of PEOPLE_PRIVATE fields unless the owner ticks "include names".
- The owner moves the file to the laptop (Office Kit) → the existing monthly DPO run of the twin project consumes it. The phone never trains.

---

## 6. F16 — Voice enrolment: "talk for 3 minutes"

### 6.1 What the 3 minutes produce

| Output | Used by | Status target |
|---|---|---|
| `ownerVoiceprint` (speaker embedding) | F12 me-attribution | Hyderabad ✅ |
| `enrolment_clean.wav` (16–24 kHz, VAD-trimmed, loudness-normalised) + transcript | Voice clone reference / fine-tune data | Hyderabad ✅ (stored) |
| `voice_profile.json` (pitch range, speaking rate, language mix) | TTS prosody defaults, persona | Hyderabad ✅ |
| Cloned TTS voice | F19 reminders, optional voice notes | Hyderabad ⚪ / Finale ✅ |

### 6.2 Enrolment script — `assets/twin/enrolment_script.json`

Shown one line at a time; the owner reads; the app records per line (per-line WAVs + transcript alignment make any later fine-tune much easier). Target ≈ 3 minutes including pauses. Lines cover numbers, dates, names, questions, emphasis, English and romanised Hindi.

```json
{
  "consent": "I am recording my own voice to create a voice model for my personal use on this phone.",
  "lines": [
    "Hi, this is my voice, recorded on my own phone.",
    "I'll send you the revised form by Friday evening.",
    "Can we move the review to Monday at ten thirty?",
    "The meeting is on the twenty-seventh of September, two thousand twenty-six.",
    "Please call me back on nine nine one five, seven three, eight nine seven five.",
    "That's great news — honestly, I didn't expect it this soon!",
    "Hmm, I'm not sure. Let me check and get back to you.",
    "Quick reminder: the report is due tomorrow, not today.",
    "Main kal tak report bhej dunga, pakka.",
    "Haan bhai, sab theek hai, tension mat le.",
    "Aaj shaam tak call kar lena, main free hoon.",
    "Mujhe thoda time chahiye, parso tak ho jayega.",
    "Numbers: one, two, three, four, five, six, seven, eight, nine, ten.",
    "Ek, do, teen, char, paanch, chhe, saat, aath, nau, das.",
    "Could you share the slides before the call?",
    "Thank you so much, I really appreciate it.",
    "Sorry for the delay — I'll fix it right away.",
    "Let's keep it simple: one screen, one button, one promise.",
    "Is it Wednesday already? Time flies.",
    "Okay, final answer: yes, we'll do it.",
    "Please read the whole message before replying.",
    "Good morning! Hope your week is going well.",
    "Dhanyavaad, milte hain agle hafte.",
    "This is the end of my voice recording."
  ]
}
```

(Owner may substitute their own phone number line with a neutral one; the recording stays on the device either way.)

### 6.3 Consent gate (hard requirement)

1. First screen explains in plain words: what is recorded, where it is stored (this phone only), what it is used for, how to delete it.
2. Owner reads the `consent` sentence aloud.
3. ASR transcribes it; `ConsentVerifier` requires word-level similarity ≥ 0.8 to the consent text **and** the consent clip's speaker embedding must match the embedding from the enrolment lines (same speaker for consent and data).
4. Only then are the enrolment lines recorded. Consent WAV + timestamp stored with the voice profile.
5. No other entry point to create a voice model. No audio import for cloning.

### 6.4 Voice-clone engine tiers

Punjabi TTS is out of scope on-device (the twin project's own bake-off found no local engine that handles Punjabi). Target **English + Hindi**.

| Tier | Approach | Where it runs | Needs | Hyderabad? |
|---|---|---|---|---|
| **C (guaranteed)** | No clone. Offline Android system TTS voice (`en-IN` / `hi-IN`) with the owner's pitch/rate from `voice_profile.json` | Phone | Offline voice data downloaded in Settings → Text-to-speech before the event | ✅ |
| **A (zero-shot, on-phone)** | A zero-shot / reference-prompt TTS model that clones from a short reference clip, exported to ONNX and run through sherpa-onnx TTS | Phone | **Verify first:** check the sherpa-onnx TTS model list for zero-shot (reference-audio) models and their language coverage. If none supports Hindi acceptably, use it for English only. | ⚪ bake-off |
| **B (fine-tuned, on-phone inference)** | Fine-tune a small single-speaker VITS/Piper-class voice from the 3-min per-line WAVs on the laptop/rented GPU, export to ONNX, push to the phone, run via sherpa-onnx | Train: laptop / rented GPU · Infer: phone | Per-line WAV + transcript pairs (§6.2 gives these); several hours of training; quality at 3 min is "recognisable", not perfect | Finale ✅ |

Rules:
- Run a **bake-off with evidence** (the twin project's method): same 5 test sentences per tier, record MOS-style 1–5 ratings from 3 listeners + speaker-embedding similarity to the owner voice-print. Keep the numbers in `docs/voice_bakeoff.md`.
- `VoiceCloneEngine` interface:

```kotlin
interface VoiceCloneEngine {
    val name: String                      // "SystemTTS·en-IN", "ZeroShot·<model>", "FineTuned·VITS"
    val languages: Set<String>
    suspend fun load()
    suspend fun synthesize(text: String, lang: String): ShortArray  // 16–24 kHz PCM
    fun close()
}
```

### 6.5 Where the cloned voice is used

| Use | Default | Rule |
|---|---|---|
| F19 own-voice reminders to the owner ("You promised Alex the form by Friday — it's Thursday.") | ON | Plays only on the owner's own phone. "AI voice" chip visible. |
| Reading a draft aloud before sending | ON | Preview only, owner's device. |
| Voice-note draft to another person | **OFF** | Requires Settings toggle + per-message confirm + disclosure prefix (§6.6). |
| Anything automatic / scheduled to other people | **Never** | Not implemented. |

### 6.6 Disclosure and provenance

- Every synthetic clip starts with a short spoken prefix when shared: *"AI voice message from [owner name]'s assistant."*
- Exported audio files get a metadata tag (`comment=Tachyon synthetic voice`) and filename suffix `_ai_voice`.
- Stretch (Finale): inaudible watermark — only if an established open-source audio watermarking model runs on-device; otherwise do not claim watermarking.

---

## 7. F17 — Behaviour interview: "talk with it for 30 minutes"

### 7.1 Goal

Build a confirmed, evidence-backed `persona.json` from a spoken Q&A, so the Planner, Twin drafts, and risk chips behave like the owner. It is a **basic** interview: plain questions, one adaptive follow-up at most, no psychometrics.

### 7.2 Flow

```
Intro + consent (30 s)
   └─► for each of 8 sections (~3.5 min each):
          ask Q (text on screen + TTS voice, Tier C/A)
          record answer (VAD end-of-answer: 2.5 s silence, max 90 s)
          ASR → answer text (shown live, editable)
          Twin decides: follow-up? (max 1 per question, only if answer < 12 words or vague)
          section end → Twin summarises section → TraitProposal[] with evidence quotes
   └─► Trait Review screen: each trait ✓ confirm / ✎ edit / ✗ reject
   └─► persona.json written (confirmed traits only) + PreferencePairs (TRAIT_DECISION)
```

- **Resumable**: sections saved as they complete; the owner can stop after any section.
- **Demo mode (stage)**: sections 1, 3, 5 with 2 questions each ≈ 5 minutes, or replay a recorded interview.
- **Summarisation in chunks**: never feed the full 30-minute transcript to the 4B model at once — summarise per section, then merge (context limit + latency).

### 7.3 Question bank — `assets/twin/questions.json`

Owner can answer in English, Hindi or Hinglish. Each question has an `id`, `section`, `text`, and `targets` (persona fields it informs).

| § | Section | Questions (basic) | Persona fields |
|---|---|---|---|
| 1 | **About you** | 1. What should I call you? 2. What do you do on a normal weekday? 3. Which languages do you use, and with whom? 4. What are you working on most right now? | `name`, `languages`, `role`, `current_focus` |
| 2 | **Your people** | 5. Who do you talk to most for work? What's their role? 6. Who do you talk to most outside work? 7. Who do you make promises to most often? 8. Is there anyone you're always extra formal with? | `people[]` (names, relation, register) |
| 3 | **How you communicate** | 9. How would you usually reply to your manager saying "Please send the update"? Say it exactly. 10. And to a close friend asking the same? 11. Do you prefer calls, WhatsApp, or email for follow-ups? 12. How do you usually sign off a message? | `style_rules`, `style_examples`, `channels`, `sign_offs` |
| 4 | **How you commit** | 13. When someone asks for something, do you say yes quickly or think first? 14. What kind of tasks do you usually underestimate? 15. How much time do you really need for a report or a document? 16. What do you do when you're going to miss a deadline? | `commit_style`, `underestimates`, `self_reported_durations`, `slip_behaviour` |
| 5 | **Your day and energy** | 17. When are you sharpest in the day? 18. When should I never remind you? 19. How many reminders before it gets annoying? 20. Weekends — do work reminders count? | `peak_hours`, `quiet_hours`, `reminder_tolerance`, `weekend_policy` |
| 6 | **How you decide** | 21. When two tasks clash, what wins? 22. What's a promise you'd never break? 23. What makes you say no to a request? 24. Do you prefer a plan or figuring it out as you go? | `priorities`, `non_negotiables`, `decline_rules`, `planning_style` |
| 7 | **What matters to you** | 25. What are you trying to achieve this year? 26. What does a good week look like for you? 27. What do people rely on you for? | `goals`, `good_week`, `reliability_areas` |
| 8 | **How Tachyon should behave** | 28. How blunt should I be when a deadline looks unrealistic? 29. Should drafts sound more formal or more like you normally talk? 30. Anything I should never do or say on your behalf? | `assistant_tone`, `draft_formality`, `hard_limits` |

Sample JSON entry:

```json
{ "id": "q15", "section": 4,
  "text": "How much time do you really need for a report or a document?",
  "text_hi": "Ek report ya document ke liye aapko sach mein kitna time lagta hai?",
  "targets": ["self_reported_durations"],
  "followup_hint": "Ask for a number of hours or days if missing." }
```

### 7.4 Trait extraction prompt (per section)

```
You build a personal profile from an interview section. Output JSON only.

For each trait you can support, output:
  field (one of the target fields listed), value (short, plain words),
  evidence (EXACT quote from the answers), confidence 0.0–1.0.

Rules:
- Only state what the person actually said. Do not infer personality types,
  mental health, medical conditions, religion, caste, politics or finances.
- If the answers do not support a field, omit it.
- Keep values short (max 15 words) and in English; evidence stays in the
  original language.
Target fields: {TARGET_FIELDS}
Answers:
{SECTION_ANSWERS}
```

`SchemaValidator` rules apply (evidence must appear verbatim in the answers; drop confidence < 0.5; drop any field outside the target list).

### 7.5 Trait Review screen

- One card per proposed trait: field label, value, evidence quote, ✓ / ✎ / ✗.
- Only ✓ or ✎ traits are written to `persona.json` with `source: "interview"`, `confirmedAt`.
- Every decision becomes a `PreferencePair(kind = TRAIT_DECISION)`.
- Settings → **My profile** lists every trait with its evidence; each can be deleted.

### 7.6 Blocked trait categories (code-enforced)

`TraitFilter` rejects any trait whose field or value matches: health/diagnosis/mental-health terms, personality-type labels (MBTI etc.), religion, caste, political views, sexual orientation, income/debt/balances. This is enforced in code with a keyword + field allow-list, not just in the prompt. Unit-test with adversarial answers.

---

## 8. Data contracts

### 8.1 `persona.json` (on device: `files/twin/persona.json`)

```json
{
  "version": 1,
  "owner": { "name": "…", "languages": ["en", "hi"], "role": "…" },
  "style": {
    "rules": ["Short sentences", "Uses 'bhai' with friends", "Signs off with 'Thanks'"],
    "examples": [
      { "register": "formal", "text": "…" },
      { "register": "casual_hinglish", "text": "…" }
    ],
    "sign_offs": ["Thanks", "Cheers"]
  },
  "commit": {
    "commit_style": "…",
    "underestimates": ["reports"],
    "self_reported_durations": { "report": "2 days", "slides": "1 day" },
    "slip_behaviour": "…"
  },
  "rhythm": { "peak_hours": "…", "quiet_hours": "22:00-08:00", "reminder_tolerance": 2, "weekend_policy": "no work reminders" },
  "decide": { "priorities": ["…"], "non_negotiables": ["…"], "decline_rules": ["…"] },
  "assistant": { "tone": "direct", "draft_formality": "like me", "hard_limits": ["…"] },
  "traits": [
    { "field": "…", "value": "…", "evidence": "…", "source": "interview", "confirmedAt": 0 }
  ]
}
```

### 8.2 `people.json`

```json
[
  { "id": "p_alex", "name": "Alex", "aliases": ["Alex sir"], "relation": "manager",
    "register": "formal", "channel": "email", "trust_tier": "WORK" },
  { "id": "p_sam", "name": "Sam", "aliases": ["Sammy"], "relation": "friend",
    "register": "casual_hinglish", "channel": "whatsapp", "trust_tier": "PERSONAL" }
]
```

At the hackathon ship `people.demo.json` with **fictional** people only.

### 8.3 Other tables

```kotlin
@Entity data class VoiceProfile(
    @PrimaryKey val id: String = "owner",
    val voiceprint: ByteArray,          // float32 L2-normalised
    val threshold: Float,
    val consentWavPath: String, val consentAt: Long,
    val enrolmentDir: String,           // per-line WAVs + transcript.jsonl
    val cloneEngine: String?,           // null until a clone tier is ready
    val pitchHzMedian: Float, val wordsPerMin: Float
)

@Entity data class InterviewAnswer(
    @PrimaryKey val id: String,
    val interviewId: String, val questionId: String,
    val answerText: String, val audioPath: String?, val createdAt: Long
)

@Entity data class LedgerEntry(          // F10
    @PrimaryKey val seq: Long,
    val commitmentHash: String, val payloadJson: String,
    val sigOwner: String, val sigCounterparty: String?,
    val prevHash: String, val entryHash: String, val createdAt: Long
)
```

---

## 9. Guardian — trust tiers

| Tier | Contents | Can leave the app via |
|---|---|---|
| `PUBLIC_COMMITMENT` | owner name, task, deadline, to-whom name | Handshake QR, share sheet, calendar |
| `WORK` | people.json WORK entries, work register | Share sheet (drafts to that person only) |
| `PERSONAL` | PERSONAL people, persona style/examples, interview answers | Never, except the owner's own "Export for twin training" |
| `BIOMETRIC` | voice-print, enrolment WAVs, consent clip, voice model | Never. Not included in any export. |

`TrustPolicy.strip(payload, destination)` runs on every outbound path. Unit tests: Handshake payload contains no `persona`, `people.register`, `voiceprint`, or interview text.

---

## 10. Privacy, deletion, loaner device

### 10.1 Delete my twin (Settings)
Wipes: `VoiceProfile`, enrolment + consent WAVs, clone model files, `persona.json`, `people.json`, `InterviewAnswer`, `PreferencePair`, Keystore alias. Shows a checklist of what was deleted.

### 10.2 App-level
- `android:allowBackup="false"` (already in README).
- Files under `getFilesDir()` (internal), not shared storage, except explicit exports.

### 10.3 Before returning the loaner phone
- [ ] Tachyon → Settings → Delete my twin
- [ ] Uninstall Tachyon; Termux: `rm -rf ~/models ~/src ~/ErayaXiqoo`, then uninstall Termux + Termux:API
- [ ] Remove Google/vivo accounts; Settings → Reset → Erase all data

---

## 11. F10 Handshake and F11 Eyes (summary spec)

### F10 Handshake
- Keystore EC P-256 keypair on first launch (`KeystoreSigner`).
- Canonical JSON of an ACCEPTED commitment (sorted keys, UTF-8, `PUBLIC_COMMITMENT` tier only) → SHA-256 → `SHA256withECDSA`.
- QR via ZXing core; scan via CameraX + ML Kit barcode (bundled model, offline).
- Counterparty verifies, counter-signs, shows its QR back; both append a `LedgerEntry` with `prevHash`.
- `Ledger.verifyChain()` → badge "Signed by both" / "⚠ Ledger tampered". Debug-only "tamper" button for the demo.
- Unit tests: sign/verify round-trip, chain break detection.

### F11 Eyes
- CameraX capture → ML Kit Text Recognition v2 (bundled Latin + Devanagari) → existing Planner with `source = CAMERA`.
- Evidence = OCR line text + bounding-box crop on the proposal card.
- Dedup voice + camera proposals by normalised `owner + task`.
- Stretch: VLM via GenieX on NPU.

---

## 12. F18 Recall and F19 own-voice reminders

### F18 Recall
- Index only the owner's **own** records: ACCEPTED/REJECTED commitments, session summaries. Not interview answers (PERSONAL) unless the owner enables "include my profile".
- Embeddings: small multilingual embedding model, cosine top-5, plus keyword filter on person names (hybrid, like the twin's memory).
- Answer prompt must cite the commitment IDs used; UI shows the matching cards under the answer.
- Voice query → ASR → Recall → text answer (+ optional TTS in own voice).

### F19 Own-voice reminders
- `WorkManager` job per ACCEPTED commitment with a deadline: reminder at T-24 h and T-2 h, respecting `quiet_hours`, `weekend_policy`, `reminder_tolerance`.
- Notification + optional spoken line through `VoiceCloneEngine` (falls back to Tier C).
- Text built by the Twin: short, in the owner's tone setting.

---

## 13. Build order and time budget

### Hyderabad (remaining hours after the core loop passes in airplane mode)

| # | Block | Light | Est. | Done when |
|---|---|---|---|---|
| 1 | AgentBus + refactor existing pipeline into Perceiver/Planner/Recoverer | 🟢 | 1.5 h | Existing tests still pass |
| 2 | F16a enrolment UI + consent gate + per-line recording + voice-print (no clone) | 🟢 | 2 h | Voice-print stored; consent verified |
| 3 | F12 me-attribution in transcript | 🔴 tune / 🟢 code | 1.5 h | 4/5 owner lines tagged USER |
| 4 | F13 persona + people cards in prompt, schema fields, RiskScorer | 🔴 | 1.5 h | "You → Alex" + "Tight for you" chip on demo |
| 5 | F17 short interview (sections 1, 3, 4) + Trait Review | 🟢 | 2.5 h | persona.json written from confirmed traits |
| 6 | F14 drafts (persona few-shot first; LoRA if adapter ready) | 🟢 | 1.5 h | Two registers visible on stage |
| 7 | F15 preference pairs + export | 🔴 | 45 min | JSONL exported |
| 8 | F10 Handshake | 🟢 | 5 h | Two phones, signed, tamper demo |
| 9 | Voice-clone Tier C + bake-off note on Tier A | 🔴 | 1 h | Reminder spoken offline |

Cut line: if behind, drop 8 → 9 → 7. Blocks 2–6 are the story.

### Grand Finale (48 h) additions
Full 30-min interview · voice-clone Tier A/B with bake-off numbers · F18 Recall · F19 own-voice reminders · Handshake over Nearby Connections · F11 Eyes · GenieX NPU for all LLM work.

---

## 14. Test plan

| Test | Method | Pass |
|---|---|---|
| Consent gate | Wrong sentence / different speaker reading consent | Enrolment blocked |
| Voice-print | 5 owner + 5 volunteer lines | ≥ 4/5 correct each |
| Trait filter | Answers mentioning health, religion, salary | No such trait proposed or saved |
| Evidence check | Hand-crafted LLM output with invented quote | Item dropped |
| Guardian | Handshake payload JSON | No persona / people register / biometric fields |
| Drafts | Same commitment → manager vs friend | Register differs; no auto-send |
| Airplane mode | Full onboarding + demo loop, radio off | Works end to end |
| Delete my twin | Run, then inspect files dir + DB | All twin artefacts gone |
| Ledger | Edit one byte of a stored entry | "⚠ Ledger tampered" |

---

## 15. Stage demo (with twin layer)

| Step | Jury sees |
|---|---|
| 1 | Airplane mode ON on camera |
| 2 | Onboarding replay (15 s): consent line → voice-print ✓ → 3 confirmed traits from the interview |
| 3 | Scripted conversation → transcript shows **You** vs **Alex** chips |
| 4 | Proposals: "You → Alex · form by Friday" with **"Tight for you"** amber chip |
| 5 | ✓ ✓ ✗ → Draft follow-up for Alex (formal) and for Sam (Hinglish) — different styles |
| 6 | Handshake: second phone scans → "Signed by both" → tamper → "⚠ Ledger tampered" |
| 7 | Reminder plays in the owner's voice (or Tier C with honest label) with "AI voice" chip |
| 8 | Close: *"It knows who promised, who to, and how you'd say it. It learns from every tap. Nothing left the phone."* |

---

## 16. Claude Code prompts (paste one at a time)

1. *"Read CLAUDE.md and INTEGRATIONS.md. Add F10–F19 to CLAUDE.md §6. Implement §3 AgentBus and refactor the existing pipeline into Perceiver, Planner and Recoverer without changing behaviour. Run all unit tests."*
2. *"Implement §6.1–6.3: VoiceEnrolmentScreen, consent gate with ConsentVerifier, per-line WAV recording from assets/twin/enrolment_script.json, voice-print via sherpa-onnx speaker embeddings (mirror the official sherpa-onnx Android speaker-identification example; do not invent APIs). Store VoiceProfile. Tests for the consent similarity and threshold logic."*
3. *"Implement §5.1 F12 me-attribution and speaker chips with rename."*
4. *"Implement §5.2 F13: persona/people prompt blocks, schema additions, RiskScorer, 'Tight for you' chip. Load people.demo.json from assets on first run."*
5. *"Implement §7 F17 interview: questions.json, InterviewEngine with VAD end-of-answer and max one follow-up, per-section trait extraction with §7.4 prompt, TraitFilter (§7.6, code-enforced), TraitReviewScreen, persona.json writer. Include a demo mode with sections 1, 3, 4."*
6. *"Implement §5.3 F14 drafts with persona few-shot; add LoRA scale support in LlamaServerLlm only after confirming the request format in the llama.cpp server README."*
7. *"Implement §5.4 F15 and §9 Guardian TrustPolicy with unit tests."*
8. *"Implement §11 F10 Handshake with unit tests for sign/verify and chain tamper detection."*
9. *"Implement §6.4 Tier C VoiceCloneEngine (Android offline TTS) and F19 reminders via WorkManager respecting quiet hours."*

---

## 17. Disclosure line (for README + deck)

*ERAYA's agent archetypes and the Self twin project (persona modelling, style adapter, DPO learning loop) are pre-existing work by the same author. The Tachyon app scaffold was written the day before the event (tag `pre-event-scaffold`). The on-device agent bus, twin onboarding (voice enrolment, behaviour interview), personalization features, Handshake and all NPU work were built at the event.*
