# Demo script: English + Hindi (~60 s)

Two voices (Evolet + one volunteer). Three commitments are buried in small talk.

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

On stage: accept commitments 1 and 3, **reject** commitment 2 to show the human is in control.

Pass criteria (fixture `prompts/fixtures/demo.json`): 3 proposals. The WhatsApp idea and the printer line must NOT appear.

## Stage sequence

1. Pull down the notification shade → **Airplane mode ON**, on camera.
2. Open Tachyon → the badge reads "OFFLINE · NPU" (or "OFFLINE · CPU" on the fallback tier).
3. Tap record → the 60-second conversation → the live transcript arrives in chunks.
4. Tap stop → proposals appear with evidence quotes and the latency badge.
5. ✓ ✓ ✗ → the Tasks tab shows two confirmed items → "Add to calendar" on one.
6. Closing line: "Nothing left the phone. Nothing was written without a tap."

Backup: if the live mic fails, Settings → "Use sample recording" runs `sample_en_hi.wav` through the same pipeline.
