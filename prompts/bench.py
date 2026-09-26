#!/usr/bin/env python3
"""Model + agent-strategy bake-off against a running llama-server (any GGUF).

Uses /v1/chat/completions so each model gets its own chat template, with
json_schema-constrained output. Applies the app's filters (evidence in transcript,
confidence >= 0.4, hedge words). Strategies:

  single  one extractor call (temperature 0.1)
  vote3   swarm: 3 extractor agents in parallel (temperature 0.7), keep items
          that at least 2 agents agree on (needs llama-server -np 3)
  verify  extractor + verifier agent that votes firm-commitment yes/no per item

    python prompts/bench.py --label qwen3.5-2b --strategies single,vote3,verify
"""
import argparse
import concurrent.futures as cf
import glob
import json
import os
import re
import time
import unicodedata
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "prompts")
HEDGES = re.compile(r"\b(maybe|perhaps|someday|some day|might|we could|we should|should we|could we|what if|shayad|sochte hain)\b|शायद")

VERIFY_SYSTEM = (
    "You check candidate commitments from a meeting transcript. For each numbered candidate, "
    "answer true only if the quoted words are an explicit, firm promise by someone to do something "
    "(not an idea, question, hope, condition, or something already done). Return JSON only."
)
VERIFY_SCHEMA = {"type": "object", "properties": {"verdicts": {"type": "array", "items": {"type": "boolean"}}},
                 "required": ["verdicts"]}


def norm(s):
    return " ".join("".join(c if unicodedata.category(c)[0] in "LMN" else " " for c in s.lower()).split())


def chat(server, system, user, schema, temperature=0.1, max_tokens=512, seed=None):
    body = {"messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
            "temperature": temperature, "max_tokens": max_tokens, "cache_prompt": True,
            "response_format": {"type": "json_schema", "json_schema": {"name": "out", "schema": schema}}}
    if seed is not None:
        body["seed"] = seed
    req = urllib.request.Request(server.rstrip("/") + "/v1/chat/completions", json.dumps(body).encode(),
                                 {"Content-Type": "application/json"})
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=600) as r:
        out = json.load(r)
    text = out["choices"][0]["message"].get("content") or ""
    tps = (out.get("timings") or {}).get("predicted_per_second")
    return text, time.time() - t0, tps


def parse(text):
    try:
        return json.loads(text[text.find("{"):text.rfind("}") + 1]).get("commitments") or []
    except (ValueError, AttributeError):
        return None


def keep(items, transcript):
    tn = f" {norm(transcript)} "
    out = []
    for c in items or []:
        if not isinstance(c, dict):
            continue
        ev = norm(str(c.get("evidence", "")))
        try:
            conf = float(c.get("confidence") or 0)
        except (TypeError, ValueError):
            conf = 0
        if c.get("task") and ev and f" {ev} " in tn and conf >= 0.4 and not HEDGES.search(ev):
            out.append(c)
    return out


def same(a, b):
    x, y = set(norm(a["evidence"]).split()), set(norm(b["evidence"]).split())
    return len(x & y) / max(1, len(x | y)) >= 0.6


def run_single(args, system, fx, schema):
    text, dt, tps = chat(args.server, system, fx["transcript"], schema)
    return keep(parse(text), fx["transcript"]), dt, tps


def run_vote3(args, system, fx, schema):
    t0 = time.time()
    with cf.ThreadPoolExecutor(3) as ex:
        outs = list(ex.map(lambda s: chat(args.server, system, fx["transcript"], schema, 0.7, seed=s), [11, 22, 33]))
    runs = [keep(parse(t), fx["transcript"]) for t, _, _ in outs]
    clusters = []  # [representative, votes]
    for run in runs:
        for c in run:
            for cl in clusters:
                if same(cl[0], c):
                    cl[1] += 1
                    break
            else:
                clusters.append([c, 1])
    return [c for c, v in clusters if v >= 2], time.time() - t0, outs[0][2]


def run_verify(args, system, fx, schema):
    items, dt, tps = run_single(args, system, fx, schema)
    if not items:
        return items, dt, tps
    listing = "\n".join(f'{i + 1}. "{c["evidence"]}"' for i, c in enumerate(items))
    text, dt2, _ = chat(args.server, VERIFY_SYSTEM, f"Transcript:\n{fx['transcript']}\n\nCandidates:\n{listing}",
                        VERIFY_SCHEMA, max_tokens=64)
    try:
        verdicts = json.loads(text[text.find("{"):text.rfind("}") + 1])["verdicts"]
    except (ValueError, KeyError):
        verdicts = [True] * len(items)
    return [c for c, ok in zip(items, verdicts + [True] * len(items)) if ok], dt + dt2, tps


STRATEGIES = {"single": run_single, "vote3": run_vote3, "verify": run_verify}


def score(items, fx):
    used, tp = set(), 0
    for exp in fx["expected"]:
        n = exp["evidence_contains"].lower()
        hit = next((i for i, c in enumerate(items) if i not in used and n in c["evidence"].lower()), None)
        if hit is not None:
            used.add(hit)
            tp += 1
    return tp, len(items) - tp, len(fx["expected"]) - tp


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--server", default="http://127.0.0.1:8081")
    ap.add_argument("--label", default="model")
    ap.add_argument("--strategies", default="single,vote3,verify")
    ap.add_argument("--fixtures", default=os.path.join(ROOT, "prompts", "fixtures"))
    ap.add_argument("--prompt", default=os.path.join(ASSETS, "extract_system.txt"))
    args = ap.parse_args()
    schema = json.load(open(os.path.join(ASSETS, "schema.json"), encoding="utf-8"))
    template = open(args.prompt, encoding="utf-8").read()
    fixtures = [json.load(open(f, encoding="utf-8")) for f in sorted(glob.glob(os.path.join(args.fixtures, "*.json")))]
    for strat in args.strategies.split(","):
        tp = fp = fn = 0
        lat, tpss = [], []
        for fx in fixtures:
            system = template.replace("{TODAY}", fx["today"]).replace("{WEEKDAY}", "Saturday")
            items, dt, tps = STRATEGIES[strat](args, system, fx, schema)
            a, b, c = score(items, fx)
            tp, fp, fn = tp + a, fp + b, fn + c
            lat.append(dt)
            if tps:
                tpss.append(tps)
        p = tp / (tp + fp) if tp + fp else 1.0
        r = tp / (tp + fn) if tp + fn else 1.0
        f1 = 2 * p * r / (p + r) if p + r else 0
        lat.sort()
        print(json.dumps({"model": args.label, "strategy": strat, "tp": tp, "fp": fp, "fn": fn,
                          "precision": round(p, 2), "recall": round(r, 2), "f1": round(f1, 2),
                          "median_s": round(lat[len(lat) // 2], 1), "max_s": round(lat[-1], 1),
                          "tok_s": round(sum(tpss) / len(tpss), 1) if tpss else None}), flush=True)


if __name__ == "__main__":
    main()
