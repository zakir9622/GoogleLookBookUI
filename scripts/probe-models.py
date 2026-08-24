#!/usr/bin/env python3
"""Smoke-test every curated Lookbook cloud model against live APIs.

Reads tokens from local.properties (lookbook.hf.token, lookbook.openrouter.token,
lookbook.groq.token) or LOOKBOOK_* environment variables.

Usage:
  python3 scripts/probe-models.py [--quick] [--json out.json]

Exit 0 when every *required* model passes; exit 1 otherwise.
Required models: FLUX (image gen), at least one code LLM with configured keys.
Visual ZeroGPU Spaces may fail when the daily allowance is spent — those are
reported as WARN, not FAIL, unless --strict is passed.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
PNG_B64 = (
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
)
IMG_DATA = f"data:image/png;base64,{PNG_B64}"


def load_props() -> dict[str, str]:
    props: dict[str, str] = {}
    lp = ROOT / "local.properties"
    if lp.exists():
        for line in lp.read_text().splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            props[k.strip()] = v.strip()
    for env, key in (
        ("LOOKBOOK_HF_TOKEN", "lookbook.hf.token"),
        ("LOOKBOOK_OPENROUTER_TOKEN", "lookbook.openrouter.token"),
        ("LOOKBOOK_GROQ_TOKEN", "lookbook.groq.token"),
    ):
        if os.environ.get(env):
            props[key] = os.environ[env]
    return props


def file_data(url: str) -> dict[str, Any]:
    return {
        "path": None,
        "url": url,
        "size": None,
        "orig_name": "input.png",
        "mime_type": "image/png",
        "is_stream": False,
        "meta": {"_type": "gradio.FileData"},
    }


def image_editor(url: str) -> dict[str, Any]:
    return {"background": file_data(url), "layers": [], "composite": None}


SPACE_CASES = [
    ("flux-schnell-hf", "black-forest-labs-flux-1-schnell.hf.space", "infer",
     ["modest fashion portrait", 0, True, 1024, 1024, 4], "IMAGE_GEN", True),
    ("sdxl-lightning-hf", "ByteDance-SDXL-Lightning.hf.space", "generate_image",
     ["modest fashion portrait", "4-Step"], "IMAGE_GEN", False),
    ("qwen-image-edit-hf", "multimodalart-qwen-image-edit-fast.hf.space", "infer",
     [file_data(IMG_DATA), "make the dress blue", 0, True, 1.0, 8, False], "IMAGE_EDIT", False),
    ("instruct-pix2pix-hf", "timbrooks-instruct-pix2pix.hf.space", "generate",
     [file_data(IMG_DATA), "make it blue", 8, "Randomize Seed", 42, "Fix CFG", 7.5, 1.5],
     "IMAGE_EDIT", False),
    ("ootd-hf", "levihsu-ootdiffusion.hf.space", "process_hd",
     [file_data(IMG_DATA), file_data(IMG_DATA), 1, 20, 2.0, 42], "TRY_ON", False),
    ("ltx-zerogpu-hf", "DeepRat-LTX-Video-ZeroGPU-Optimized.hf.space", "text_to_video",
     ["fashion runway walk", "worst quality", None, None, 512, 704, "text-to-video",
      2.0, 9, 42, True, 1.0, True, False], "VIDEO", False),
    ("wan2-video-hf", "OpenKing-wan2-video-generation.hf.space", "generate_video",
     ["fashion runway", None, 832, 480, 33, 25, 5.0, -1], "VIDEO", False),
]

LLM_CASES = [
    ("qwen25-coder-7b-hf", "HF_INFERENCE", "Qwen/Qwen2.5-Coder-7B-Instruct", "lookbook.hf.token"),
    ("qwen25-coder-hf", "HF_INFERENCE", "Qwen/Qwen2.5-Coder-32B-Instruct", "lookbook.hf.token"),
    ("openrouter-free", "OPENROUTER", "openrouter/free", "lookbook.openrouter.token"),
    ("llama33-70b-groq", "GROQ", "llama-3.3-70b-versatile", "lookbook.groq.token"),
]


@dataclass
class Result:
    model_id: str
    status: str  # OK, WARN, FAIL, SKIP
    detail: str
    seconds: float = 0.0


def probe_space(host: str, api: str, data: list[Any], token: str | None, timeout: float = 120) -> str:
    prefixes = ["/gradio_api", ""]
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    payload = json.dumps({"data": data}, default=str).encode()

    event_id = None
    prefix = None
    for p in prefixes:
        url = f"https://{host}{p}/call/{api}"
        req = urllib.request.Request(url, data=payload, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                body = resp.read().decode()
                m = re.search(r'"event_id"\s*:\s*"([^"]+)"', body)
                if m:
                    event_id = m.group(1)
                    prefix = p
                    break
        except urllib.error.HTTPError as e:
            if e.code == 404:
                continue
            return f"HTTP {e.code}: {e.read()[:180].decode(errors='replace')}"
        except Exception as e:
            return str(e)[:180]

    if not event_id:
        return "no event_id"

    poll_url = f"https://{host}{prefix}/call/{api}/{event_id}"
    start = time.time()
    while time.time() - start < timeout:
        req = urllib.request.Request(poll_url, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                body = resp.read().decode()
                if "event: error" in body or '"event":"error"' in body:
                    lines = [l for l in body.split("\n") if l.startswith("data:")]
                    detail = lines[-1][5:].strip()[:220] if lines else body[:220]
                    if "null" in detail and len(detail) < 12:
                        return "empty error (ZeroGPU quota or waking)"
                    return f"error: {detail}"
                if "event: complete" in body or '"event":"complete"' in body:
                    return f"OK ({time.time() - start:.1f}s)"
        except Exception as e:
            return f"poll: {e}"
        time.sleep(2)
    return "TIMEOUT"


def probe_llm(platform: str, model: str, token: str) -> str:
    if platform == "HF_INFERENCE":
        url = "https://router.huggingface.co/v1/chat/completions"
    elif platform == "OPENROUTER":
        url = "https://openrouter.ai/api/v1/chat/completions"
    elif platform == "GROQ":
        url = "https://api.groq.com/openai/v1/chat/completions"
    else:
        return f"unknown platform {platform}"
    data = json.dumps({
        "model": model,
        "messages": [{"role": "user", "content": "print hello in python"}],
        "max_tokens": 40,
    }).encode()
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    if platform == "OPENROUTER":
        headers["HTTP-Referer"] = "https://github.com/zakir9622/Agentic-AI"
        headers["X-Title"] = "The Lookbook probe"
    try:
        req = urllib.request.Request(url, data=data, headers=headers)
        with urllib.request.urlopen(req, timeout=60) as resp:
            body = json.loads(resp.read())
            msg = body["choices"][0]["message"]
            text = msg.get("content") or msg.get("reasoning") or str(msg)
            return f"OK: {text[:60]}"
    except Exception as e:
        return f"FAIL: {e}"


def probe_inference_image(model: str, token: str) -> str:
    payload = json.dumps({
        "response_format": "b64_json",
        "prompt": "modest fashion portrait",
        "model": model,
        "size": "512x512",
    }).encode()
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    try:
        req = urllib.request.Request(
            "https://router.huggingface.co/nscale/v1/images/generations",
            data=payload,
            headers=headers,
        )
        with urllib.request.urlopen(req, timeout=120) as resp:
            body = json.loads(resp.read())
            b64 = body["data"][0]["b64_json"]
            return f"OK ({len(b64)} b64 chars)"
    except Exception as e:
        return f"FAIL: {e}"


def probe_inference_edit(model: str, token: str) -> str:
    payload = json.dumps({
        "response_format": "b64_json",
        "prompt": "make it blue",
        "model": model,
        "size": "512x512",
        "image": PNG_B64,
    }).encode()
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    try:
        req = urllib.request.Request(
            "https://router.huggingface.co/nscale/v1/images/edits",
            data=payload,
            headers=headers,
        )
        with urllib.request.urlopen(req, timeout=120) as resp:
            body = json.loads(resp.read())
            b64 = body["data"][0]["b64_json"]
            return f"OK ({len(b64)} b64 chars)"
    except Exception as e:
        detail = str(e)
        if "402" in detail:
            return f"WARN: {detail[:120]}"
        if "400" in detail:
            return f"WARN: {detail[:120]}"
        return f"FAIL: {detail[:180]}"


def probe_router_models(token: str) -> str:
    headers = {"Authorization": f"Bearer {token}"}
    try:
        req = urllib.request.Request("https://router.huggingface.co/v1/models", headers=headers)
        with urllib.request.urlopen(req, timeout=30) as resp:
            body = json.loads(resp.read())
            models = body.get("data") or []
            ids = [m.get("id", "") for m in models[:5]]
            return f"OK ({len(models)} models; sample: {', '.join(ids)})"
    except Exception as e:
        return f"FAIL: {e}"


def classify_space(detail: str, required: bool, strict: bool) -> str:
    if detail.startswith("OK"):
        return "OK"
    if any(x in detail.lower() for x in ("zerogpu", "quota", "0s left", "empty error", "no gpu")):
        return "FAIL" if (required and strict) else "WARN"
    if detail.startswith("HTTP 503") or "queue is full" in detail.lower():
        return "WARN"
    return "FAIL" if required else "WARN"


def main() -> int:
    parser = argparse.ArgumentParser(description="Probe Lookbook cloud models")
    parser.add_argument("--quick", action="store_true", help="Only FLUX + configured LLMs")
    parser.add_argument("--strict", action="store_true", help="Treat ZeroGPU quota as FAIL")
    parser.add_argument("--json", metavar="PATH", help="Write JSON report")
    args = parser.parse_args()

    props = load_props()
    hf = props.get("lookbook.hf.token")
    results: list[Result] = []

    cases = SPACE_CASES if not args.quick else [c for c in SPACE_CASES if c[0] == "flux-schnell-hf"]
    for mid, host, api, data, _cap, required in cases:
        t0 = time.time()
        detail = probe_space(host, api, data, hf, timeout=90 if args.quick else 120)
        status = classify_space(detail, required, args.strict)
        results.append(Result(mid, status, detail, time.time() - t0))

    if hf:
        t0 = time.time()
        detail = probe_inference_image("black-forest-labs/FLUX.1-schnell", hf)
        status = "OK" if detail.startswith("OK") else ("WARN" if "402" in detail else "FAIL")
        results.append(Result("flux-schnell-inference", status, detail, time.time() - t0))

        t0 = time.time()
        detail = probe_inference_edit("timbrooks/instruct-pix2pix", hf)
        status = "OK" if detail.startswith("OK") else ("WARN" if detail.startswith("WARN") else "FAIL")
        results.append(Result("instruct-pix2pix-inference", status, detail, time.time() - t0))

        t0 = time.time()
        detail = probe_router_models(hf)
        status = "OK" if detail.startswith("OK") else "FAIL"
        results.append(Result("hf-router-models", status, detail, time.time() - t0))

    for mid, platform, model, prop_key in LLM_CASES:
        token = props.get(prop_key)
        if not token:
            results.append(Result(mid, "SKIP", f"no {prop_key}"))
            continue
        t0 = time.time()
        detail = probe_llm(platform, model, token)
        status = "OK" if detail.startswith("OK") else ("WARN" if "402" in detail else "FAIL")
        results.append(Result(mid, status, detail, time.time() - t0))

    print(f"{'MODEL':32s} {'STATUS':6s} DETAIL")
    print("-" * 80)
    for r in results:
        print(f"{r.model_id:32s} {r.status:6s} {r.detail}")

    fails = [r for r in results if r.status == "FAIL"]
    warns = [r for r in results if r.status == "WARN"]
    print(f"\n{len([r for r in results if r.status=='OK'])} OK, {len(warns)} WARN, {len(fails)} FAIL, "
          f"{len([r for r in results if r.status=='SKIP'])} SKIP")

    if args.json:
        Path(args.json).write_text(json.dumps([r.__dict__ for r in results], indent=2))

    llm_ok = any(r.model_id in {c[0] for c in LLM_CASES} and r.status in ("OK", "WARN") for r in results)
    flux = next((r for r in results if r.model_id in ("flux-schnell-hf", "flux-schnell-inference")), None)
    flux_okish = flux and flux.status in ("OK", "WARN")

    if fails or not llm_ok:
        return 1
    if args.strict and warns:
        return 1
    if not flux_okish:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
