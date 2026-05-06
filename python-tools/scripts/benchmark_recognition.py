from __future__ import annotations

import sys
import time
import argparse
import json
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
BACKEND_ROOT = REPO_ROOT / "python-backend"
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

from app.services.recognition_service import RecognitionService  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser(description="Benchmark CampusVista photo recognition.")
    parser.add_argument("--json", action="store_true", help="Emit machine-readable JSON.")
    args = parser.parse_args()

    service = RecognitionService()
    coverage = service.get_coverage_summary()
    rows = [row for row in service.get_reference_labels() if row["supported"]]
    top1 = 0
    top3 = 0
    rejected = 0
    timings: list[float] = []

    for row in rows:
        image_file = row["image_file"]
        expected_checkpoint = row["checkpoint_id"]
        image_path = BACKEND_ROOT / "data" / "pano" / "outdoor" / image_file
        started = time.perf_counter()
        result = service.recognize(image_path.read_bytes(), "image/jpeg")
        timings.append(time.perf_counter() - started)
        matches = result["matches"]
        if not result["recognized"]:
            rejected += 1
        ranked_ids = [match["checkpoint_id"] for match in matches]
        if ranked_ids and ranked_ids[0] == expected_checkpoint:
            top1 += 1
        if expected_checkpoint in ranked_ids[:3]:
            top3 += 1

    total = max(1, len(rows))
    report = {
        "samples": len(rows),
        "top1": round(top1 / total, 3),
        "top3": round(top3 / total, 3),
        "rejected": round(rejected / total, 3),
        "average_latency_ms": round(sum(timings) / total * 1000, 1),
        "coverage": coverage,
        "note": "Reference-image smoke benchmark, not real-phone-photo accuracy.",
    }
    if args.json:
        print(json.dumps(report, indent=2, sort_keys=True))
        return

    print("Recognition benchmark on reference pano images")
    print(f"- samples: {report['samples']}")
    print(f"- top1: {report['top1']:.3f}")
    print(f"- top3: {report['top3']:.3f}")
    print(f"- rejected: {report['rejected']:.3f}")
    print(f"- average_latency_ms: {report['average_latency_ms']:.1f}")
    print(f"- coverage: {coverage['supported_checkpoint_count']}/{coverage['checkpoint_count']} checkpoints")
    print(f"Note: {report['note']}")


if __name__ == "__main__":
    main()
