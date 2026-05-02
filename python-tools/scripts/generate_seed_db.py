from __future__ import annotations

import argparse
from pathlib import Path

from campusvista_data import generate_all, import_real_data


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate CampusVista seed data.")
    parser.add_argument("--workbook", type=Path, help="Excel workbook source of truth.")
    parser.add_argument("--map-pdf", type=Path, help="Campus map PDF with numbered checkpoint markers.")
    parser.add_argument("--panos-zip", type=Path, help="ZIP containing available outdoor pano images.")
    args = parser.parse_args()

    if args.workbook or args.map_pdf or args.panos_zip:
        if not (args.workbook and args.map_pdf and args.panos_zip):
            parser.error("--workbook, --map-pdf, and --panos-zip must be supplied together")
        summary = import_real_data(args.workbook, args.map_pdf, args.panos_zip)
        print("Real data imported:")
        for label, count in summary.items():
            print(f"- {label}: {count}")

    outputs = generate_all()
    print("Seed DB generated and Android assets updated:")
    for label, path in outputs.items():
        print(f"- {label}: {path}")


if __name__ == "__main__":
    main()
