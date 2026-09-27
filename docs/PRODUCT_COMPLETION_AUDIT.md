# ERAYA product completion audit

Updated: 27 September 2026. This file separates implemented code from phone-proven behaviour. A green unit test is not treated as proof of a full airplane-mode demo.

## Product thesis

ERAYA is a privacy-first, adaptive personal digital twin that runs on the owner's phone. It combines:

- The earlier personal-memory prototype contributes evidence-backed memory, recency-aware retrieval, trust tiers, persona adaptation and local-first handling. No private corpus, credentials or raw messages are copied into this repository or the phone.
- The earlier calling-agent prototype contributes deterministic routing, explicit degradation, action confirmation and auditability. No cloud vendor path or secret is imported.
- ERAYA's native differentiator: an animated portrait, private voice/chat, propose-then-confirm commitments, explainable evidence, learned communication style and on-device reminders.

The demo claim remains: **It remembers with evidence, speaks with disclosure, adapts only from confirmed signals, and acts only after a tap.**

## Requirement-by-requirement state

| Requirement | Current evidence | State | Remaining proof/work |
|---|---|---|---|
| F1-F9 core capture loop | Recorder service, chunked ASR, extractor/verifier, confirmation loop, Tasks, calendar intent and engine settings exist | Implemented | Five consecutive real-voice airplane-mode runs; Hindi real-voice run; NPU switch is not available |
| Portrait room | Portrait-only neutral/talking/blink/left/right frames; speech-amplitude jaw deformation, eye movement, blink, breath and head gestures; no 3D UI route | Implemented | Visual check of v0.18.1 on the iQOO after portrait import |
| Guarded private conversation | Local ASR/LLM/TTS controller, prompt-injection boundary and false-action-claim guard | Implemented | Longer interruption/thermal rehearsal |
| F10 signed handshake | Device-bound P-256 signing, canonical public payload, QR offer/countersign flow, bundled offline scanner, hash-chained local ledger and tamper demo | Implemented core | Complete a two-phone exchange and camera readability test on real devices; live CameraX view remains a finale enhancement |
| F11 Eyes | Private one-shot capture, bundled English+Devanagari OCR, deterministic line cleanup, editable review, explicit propose tap and source-photo deletion feed the existing guarded pipeline | Implemented core | Complete a real whiteboard/note walkthrough on the iQOO; evidence crops and live CameraX framing remain finale enhancements |
| F12 owner attribution | Transcript model supports a speaker field, but it remains null; no speaker embedding dependency | Missing | Consent-matched speaker embedding, threshold tuning and speaker chips |
| F13 people-aware extraction | Persona/people prompt blocks, IDs and deterministic risk scorer | Implemented | Real-voice stage proof with owner/person attribution depends on F12 |
| F14 twin drafts | Persona/recipient-aware draft dialog and share-sheet boundary | Implemented | Phone proof for formal versus Hinglish recipients |
| F15 learning loop | Proposal/draft/trait preference pairs, Guardian stripping and JSONL export | Implemented | Run and inspect an export on the phone |
| F16 voice enrolment | Spoken consent text check, private aligned WAV/transcript clips, pitch/rate profile | Partial | Same-speaker consent check, actual speaker embedding and optional owner-only local clone; current output is honestly labelled system TTS |
| F17 behaviour interview | Spoken/typed questions, private versioned checkpoint, safe pause/resume/start-over, interrupted-stage recovery, trait extraction/filter, evidence review and confirmed persona merge | Implemented core | Complete a full phone walkthrough, including process restart during a follow-up and Trait Review |
| F18 private recall | Deterministic recall over owner ACCEPTED/REJECTED commitments; person/task/deadline/evidence ranking; recency tilt; duplicate suppression; cited tappable source cards | Implemented core | Add multilingual embedding tier and session summaries; current lexical+recency tier remains the offline floor |
| F19 reminders | WorkManager scheduling, quiet-hour/weekend rules and labelled system-voice playback | Implemented Tier C | Phone-fire test; true owner voice requires the optional F16 clone tier |
| Navigation and production UI | Four independent Navigation 3 stacks, pushed detail/settings screens, gradients and branded launch | Implemented | Final device visual regression pass |
| CPU inference | localhost-only whisper.cpp and llama.cpp paths have worked on the iQOO | Implemented | Resilience/thermal soak; automatic process recovery remains external to the app |
| NPU inference | GenieX and WhisperKit classes intentionally throw unsupported errors | Missing | Integrate only from verified official SDK samples and on-device model packages |
| Privacy deletion | Persona, people, voice, Eyes captures, replica, handshake ledger and preferences are deleted from the UI | Partial | Verify internal files and DB after deletion on the loaner device |

## Ordered completion path

1. Prove v0.18.1 recall, portrait behaviour, Eyes and interview resume on the iQOO using real inputs.
2. Prove the F10 signed handshake across two real phones, including return QR and tamper demo.
3. Finish F12 speaker attribution because it unlocks honest "You" recognition for live multi-speaker capture.
4. Prove F11 OCR Eyes with one English/Hinglish note and one Devanagari note on the iQOO.
5. Add the optional embedding tier to F18 while keeping deterministic keyword/recency fallback visible.
6. Finish phone evidence: interview, export, reminder, calendar, Hindi and five airplane-mode full-loop runs.
7. Integrate NPU engines only if official SDK artifacts and samples are available; CPU remains the verified offline baseline.

## Completion gate

The project is not complete until every missing/partial row above is either implemented and proven or explicitly removed from the promised hackathon scope. The final audit must include APK version/hash, automated test/lint results, on-device screenshots or logs, and five recorded airplane-mode runs.
