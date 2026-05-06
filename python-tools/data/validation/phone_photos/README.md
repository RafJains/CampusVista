# Real Phone Photo Validation

Place opt-in validation photos here when real campus phone photos are available.

File names must include the expected checkpoint id so the benchmark can score the
result without a separate labeling file:

```text
OUT_CP001_front_gate_01.jpg
OUT_CP040_cafeteria_walkway_02.png
```

Run:

```bash
python3 python-tools/scripts/benchmark_recognition.py --queries-dir python-tools/data/validation/phone_photos
```

The default benchmark intentionally uses reference pano images only. This folder
is for real-photo accuracy claims.
