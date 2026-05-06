from __future__ import annotations

import argparse
import os

from campusvista_data import (
    build_recognition_index,
    build_recognition_refs,
    validate_all,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="Build CampusVista recognition reference embeddings.")
    parser.add_argument(
        "--encoder",
        choices=["handcrafted", "openclip"],
        default=None,
        help="Recognition encoder to use. Defaults to CAMPUSVISTA_RECOGNITION_ENCODER or handcrafted.",
    )
    args = parser.parse_args()
    if args.encoder:
        os.environ["CAMPUSVISTA_RECOGNITION_ENCODER"] = args.encoder

    data, _ = validate_all()
    data["recognition_refs"] = build_recognition_refs(data)
    index_path, metadata_path, android_index_path, android_labels_path = build_recognition_index(data)
    print("Recognition index generated:")
    print(f"- index: {index_path}")
    print(f"- metadata: {metadata_path}")
    print(f"- android_index: {android_index_path}")
    print(f"- android_labels: {android_labels_path}")


if __name__ == "__main__":
    main()
