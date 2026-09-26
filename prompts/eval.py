#!/usr/bin/env python3
"""Run prompt fixtures against llama-server and print precision / recall.

Mirrors the app: same system prompt + schema from app/src/main/assets/prompts, same ChatML
/completion call, same filters (strip to outermost JSON, evidence must be in the transcript,
confidence >= 0.4). Stdlib only, so it runs in Termux and on the laptop.

    python prompts/eval.py                          # all fixtures, 1 run each
    python prompts/eval.py --runs 3 --show          # stability check, print raw proposals
    python prompts/eval.py --prompt /sdcard/Android/data/com.evolet.tachyon/files/prompts/extract_system.txt

Laptop → phone: adb forward tcp:8081 tcp:8081
"""
import argparse
import datetime as dt
import glob
import json
import os
import sys
import time
import unicodedata
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "prompts")
MIN_CONFIDENCE = 0.4


def normalize(s):
    # Same as SchemaValidator.normalize: keep letters, combining marks (matras), digits.
    kept = "".join(ch if unicodedata.category(ch)[0] in "LMN" else " " for ch in s.lower())
    return " ".join(kept.split())


def evidence_found(evidence, transcript):
    e = normalize(evidence)
    return bool(e) and f" {e} " in f" {normalize(transcript)} "


def extract_json(raw):
    obj, arr = raw.find("{"), raw.find("[")
    if arr >= 0 and (obj < 0 or arr < obj):
        end = raw.rfind("]")
        if end > arr:
            return '{"commitments":' + raw[arr:end + 1] + "}"
    end = raw.rfind("}")
    return raw[obj:end + 1] if obj >= 0 and end > obj else None


def system_prompt(path, today):
    with open(path, encoding="utf-8") as f:
        text = f.read()
    return text.replace("{TODAY}", today.isoformat()).replace("{WEEKDAY}", today.strftime("%A"))


def complete(server, system, user, schema, max_tokens=512):
    prompt = (f"<|im_start|>system\n{system}<|im_end|>\n"
              f"<|im_start|>user\n{user}<|im_end|>\n<|im_start|>assistant\n"
              "<think>\n\n</think>\n\n")  # Qwen3 non-thinking mode, same as the app
    body = json.dumps({"prompt": prompt, "n_predict": max_tokens, "temperature": 0.1,
                       "cache_prompt": True, "json_schema": schema}).encode()
    req = urllib.request.Request(server.rstrip("/") + "/completion", data=body,
                                 headers={"Content-Type": "application/json"})
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=300) as r:
        out = json.load(r)
    return out.get("content", ""), (time.time() - t0) * 1000, out.get("timings", {}).get("predicted_per_second")


def run_fixture(fx, args, schema):
    today = dt.date.fromisoformat(fx["today"])
    system = system_prompt(args.prompt, today)
    raw, ms, tps = complete(args.server, system, fx["transcript"], schema)
    body = extract_json(raw)
    retried = False
    try:
        items = json.loads(body)["commitments"] if body else None
    except (ValueError, KeyError, TypeError):
        items = None
    if items is None:  # same single retry as the app
        retried = True
        raw, ms2, tps = complete(args.server, system, fx["transcript"] +
                                 "\n\nYour last output was invalid JSON. Return only valid JSON matching the schema.", schema)
        ms += ms2
        body = extract_json(raw)
        try:
            items = json.loads(body)["commitments"] if body else None
        except (ValueError, KeyError, TypeError):
            items = None
    if items is None:
        return {"invalid": True, "ms": ms, "tps": tps, "raw": raw, "retried": retried}

    kept = [c for c in items
            if isinstance(c, dict) and c.get("task") and evidence_found(c.get("evidence", ""), fx["transcript"])
            and float(c.get("confidence", 0) or 0) >= MIN_CONFIDENCE]

    matched_pred, matched_exp, deadline_ok = set(), 0, 0
    for exp in fx["expected"]:
        needle = exp["evidence_contains"].lower()
        hit = next((i for i, c in enumerate(kept) if i not in matched_pred and needle in c.get("evidence", "").lower()), None)
        if hit is not None:
            matched_pred.add(hit)
            matched_exp += 1
            if str(kept[hit].get("deadline_iso") or "").startswith(exp.get("deadline_prefix", "")):
                deadline_ok += 1
    forbidden = [w for w in fx.get("must_not_mention", [])
                 for c in kept if w in (c.get("evidence", "") + " " + c.get("task", "")).lower()]
    return {"invalid": False, "kept": kept, "dropped": len(items) - len(kept), "tp": len(matched_pred),
            "fp": len(kept) - len(matched_pred), "fn": len(fx["expected"]) - matched_exp,
            "deadline_ok": deadline_ok, "forbidden": forbidden, "ms": ms, "tps": tps, "retried": retried}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--server", default="http://127.0.0.1:8081")
    ap.add_argument("--fixtures", default=os.path.join(ROOT, "prompts", "fixtures"))
    ap.add_argument("--prompt", default=os.path.join(ASSETS, "extract_system.txt"))
    ap.add_argument("--schema", default=os.path.join(ASSETS, "schema.json"))
    ap.add_argument("--runs", type=int, default=1)
    ap.add_argument("--show", action="store_true", help="print kept proposals")
    args = ap.parse_args()

    with open(args.schema, encoding="utf-8") as f:
        schema = json.load(f)
    files = sorted(glob.glob(os.path.join(args.fixtures, "*.json")))
    if not files:
        sys.exit(f"no fixtures in {args.fixtures}")

    tp = fp = fn = invalid = 0
    latencies = []
    for path in files:
        with open(path, encoding="utf-8") as f:
            fx = json.load(f)
        for run in range(args.runs):
            r = run_fixture(fx, args, schema)
            latencies.append(r["ms"])
            tag = f"{os.path.basename(path)} #{run + 1}"
            tps = f"{r['tps']:.1f} tok/s" if r.get("tps") else "?"
            if r["invalid"]:
                invalid += 1
                print(f"✗ {tag}: INVALID JSON after retry ({r['ms']:.0f} ms)\n  raw: {r['raw'][:300]!r}")
                continue
            tp, fp, fn = tp + r["tp"], fp + r["fp"], fn + r["fn"]
            ok = r["fp"] == 0 and r["fn"] == 0 and not r["forbidden"]
            print(f"{'✓' if ok else '✗'} {tag}: tp={r['tp']} fp={r['fp']} fn={r['fn']} "
                  f"deadline_ok={r['deadline_ok']}/{r['tp']} dropped={r['dropped']} "
                  f"{'retried ' if r['retried'] else ''}{r['ms']:.0f} ms {tps}")
            if r["forbidden"]:
                print(f"  ! mentions forbidden: {sorted(set(r['forbidden']))}")
            if args.show:
                for c in r["kept"]:
                    print(f"    - {c.get('owner')} → {c.get('to_whom') or '-'}: {c.get('task')} "
                          f"[{c.get('deadline_text')} | {c.get('deadline_iso')}] conf={c.get('confidence')}\n"
                          f"      “{c.get('evidence')}”")

    precision = tp / (tp + fp) if tp + fp else 1.0
    recall = tp / (tp + fn) if tp + fn else 1.0
    latencies.sort()
    median = latencies[len(latencies) // 2] if latencies else 0
    print(f"\nprecision={precision:.2f} recall={recall:.2f} invalid={invalid} "
          f"median_latency={median / 1000:.1f} s over {len(latencies)} calls")


if __name__ == "__main__":
    main()
