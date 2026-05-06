from __future__ import annotations

from campusvista_data import (
    build_recognition_index,
    build_recognition_refs,
    validate_all,
)


def main() -> None:
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
