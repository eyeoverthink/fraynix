#!/usr/bin/env python3
"""Offline state-to-media CLI. Default: still image; --mode video: real LTX video."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import uuid
from datetime import datetime, timezone


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("state_json", nargs="?")
    parser.add_argument("--state-file")
    parser.add_argument("--mode", choices=["image", "video", "animation"], default="image")
    parser.add_argument("--output")
    for flag in ("width", "height", "frames", "fps", "steps"):
        parser.add_argument("--" + flag, type=int)
    parser.add_argument("--cpu", action="store_true")
    parser.add_argument("--quantize", action="store_true")
    args = parser.parse_args()
    if args.quantize:
        parser.error("Quantization is not implemented; CPU offload is automatic for video")
    if args.state_file:
        value = json.loads(Path(args.state_file).read_text(encoding="utf-8-sig"))
    elif args.state_json:
        value = json.loads(args.state_json) if args.state_json.lstrip().startswith("{") else json.loads(Path(args.state_json).read_text(encoding="utf-8-sig"))
    else:
        value = {"concept": "A green apple on a wooden table"}
    state = value.get("quantum_state", value)
    text = value.get("prompt") or state.get("concept", "An abstract landscape of connected ideas")
    app = Path(__file__).resolve().parent
    config = json.loads((app / "media/media-config.json").read_text())["backends"][args.mode]
    model_root = os.environ.get("FRAYNIX_MODEL_ROOT", "F:/Fraynix-Models")
    config = {k: v.replace("${MODEL_ROOT}", model_root) if isinstance(v, str) else v for k, v in config.items()}
    job_id = str(uuid.uuid4())
    directory = (app / "media_output/jobs" / job_id).resolve()
    directory.mkdir(parents=True)
    request = {"id": job_id, "action": args.mode, "text": text, "source": state,
               "output_dir": str(directory), "backend_config": config,
               "options": {key: getattr(args, key) for key in ("width", "height", "frames", "fps", "steps", "cpu") if getattr(args, key) is not None}}
    path = directory / "request.json"
    path.write_text(json.dumps(request), encoding="utf-8")
    record = dict(request, status="running", created_at=datetime.now(timezone.utc).isoformat())
    (directory / "job.json").write_text(json.dumps(record), encoding="utf-8")
    result = subprocess.run([config["python"], str(app / "media/media_worker.py"), "--request", str(path)])
    if result.returncode:
        record.update(status="failed", error="Worker failed; inspect result.json")
        (directory / "job.json").write_text(json.dumps(record), encoding="utf-8")
        return result.returncode
    evidence = json.loads((directory / "result.json").read_text(encoding="utf-8"))
    record.update(status="completed", result=evidence, finished_at=datetime.now(timezone.utc).isoformat())
    (directory / "job.json").write_text(json.dumps(record), encoding="utf-8")
    target = Path(args.output or value.get("output_filename") or f"dreamscape_output/{job_id}{Path(evidence['artifact']).suffix}")
    if target.suffix.lower() != Path(evidence["artifact"]).suffix.lower():
        parser.error("Output extension must match generated media")
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(evidence["artifact"], target)
    print(json.dumps({"artifact": str(target.resolve()), "evidence": str(directory / "result.json")}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
