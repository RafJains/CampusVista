import argparse
import json
import re
import sys
import time
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[2]
BACKEND_ROOT = REPO_ROOT / "python-backend"
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

from app.services.recognition_service import RecognitionService  # noqa: E402


SUPPORTED_IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp"}


def main() -> None:
    parser = argparse.ArgumentParser(description="Benchmark CampusVista photo recognition.")
    parser.add_argument("--json", action="store_true", help="Emit machine-readable JSON.")
    parser.add_argument(
        "--queries-dir",
        type=Path,
        default=None,
        help=(
            "Optional real-phone-photo directory. File names must contain a checkpoint id, "
            "for example OUT_CP001_01.jpg or anything with OUT_CP001 in the name."
        ),
    )
    args = parser.parse_args()

    service = RecognitionService()
    coverage = service.get_coverage_summary()
    samples = (
        phone_photo_samples(args.queries_dir)
        if args.queries_dir is not None
        else reference_samples(service)
    )
    report = benchmark_samples(service, coverage, samples)
    report["note"] = (
        "Real-phone-photo benchmark."
        if args.queries_dir is not None
        else "Reference-image smoke benchmark, not real-phone-photo accuracy."
    )
    if args.json:
        print(json.dumps(report, indent=2, sort_keys=True))
        return

    title = (
        "Recognition benchmark on real phone photos"
        if args.queries_dir is not None
        else "Recognition benchmark on reference pano images"
    )
    print(title)
    print(f"- samples: {report['samples']}")
    print(f"- top1: {report['top1']:.3f}")
    print(f"- top3: {report['top3']:.3f}")
    print(f"- rejected: {report['rejected']:.3f}")
    print(f"- average_latency_ms: {report['average_latency_ms']:.1f}")
    print(f"- coverage: {coverage['supported_checkpoint_count']}/{coverage['checkpoint_count']} checkpoints")
    print(f"Note: {report['note']}")


def reference_samples(service: RecognitionService) -> list[dict[str, Any]]:
    return [
        {
            "image_path": BACKEND_ROOT / "data" / "pano" / "outdoor" / row["image_file"],
            "expected_checkpoint": row["checkpoint_id"],
        }
        for row in service.get_reference_labels()
        if row["supported"]
    ]


def phone_photo_samples(queries_dir: Path) -> list[dict[str, Any]]:
    if not queries_dir.exists():
        raise SystemExit(f"Real-photo validation directory does not exist: {queries_dir}")

    samples: list[dict[str, Any]] = []
    checkpoint_pattern = re.compile(r"OUT_CP\d{3}")
    for image_path in sorted(queries_dir.rglob("*")):
        if image_path.suffix.lower() not in SUPPORTED_IMAGE_SUFFIXES:
            continue
        match = checkpoint_pattern.search(image_path.name)
        if match is None:
            raise SystemExit(
                "Real-photo filenames must include the expected checkpoint id: "
                f"{image_path.name}"
            )
        samples.append(
            {
                "image_path": image_path,
                "expected_checkpoint": match.group(0),
            }
        )
    if not samples:
        raise SystemExit(f"No validation photos found in {queries_dir}")
    return samples


def benchmark_samples(
    service: RecognitionService,
    coverage: dict[str, Any],
    samples: list[dict[str, Any]],
) -> dict[str, Any]:
    top1 = 0
    top3 = 0
    rejected = 0
    timings: list[float] = []

    for sample in samples:
        image_path = sample["image_path"]
        expected_checkpoint = sample["expected_checkpoint"]
        started = time.perf_counter()
        result = service.recognize(image_path.read_bytes(), content_type_for(image_path))
        timings.append(time.perf_counter() - started)
        matches = result["matches"]
        if not result["recognized"]:
            rejected += 1
        ranked_ids = [match["checkpoint_id"] for match in matches]
        if ranked_ids and ranked_ids[0] == expected_checkpoint:
            top1 += 1
        if expected_checkpoint in ranked_ids[:3]:
            top3 += 1

    total = max(1, len(samples))
    return {
        "samples": len(samples),
        "top1": round(top1 / total, 3),
        "top3": round(top3 / total, 3),
        "rejected": round(rejected / total, 3),
        "average_latency_ms": round(sum(timings) / total * 1000, 1),
        "coverage": coverage,
    }


def content_type_for(image_path: Path) -> str:
    suffix = image_path.suffix.lower()
    if suffix == ".png":
        return "image/png"
    if suffix == ".webp":
        return "image/webp"
    return "image/jpeg"


if __name__ == "__main__":
    main()
