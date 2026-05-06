# Photo Recognition Implementation

CampusVista photo recognition is implemented as hybrid visual place recognition.

## Phase Status

- Phase 1, backend retrieval baseline: complete.
- Phase 2, mobile runtime: complete as an Android local retrieval fallback using packaged embeddings. A TFLite encoder is still the preferred future upgrade once a real model artifact is available.
- Phase 3, accuracy improvement: benchmark tooling is complete, including a real-phone-photo validation mode. Real 8/10 phone-photo accuracy still requires a held-out phone-photo validation set.

## Runtime Flow

- Android lets the user take a photo or choose an image from gallery.
- The image is resized, JPEG-compressed, and sent to `POST /recognize`.
- The backend compares the photo embedding against pano-derived reference embeddings and returns ranked checkpoint matches.
- If the backend is unavailable, Android uses `assets/ml/recognition_index.bin` and `assets/ml/recognition_index_labels.csv` for local fallback matches.
- `GET /recognition/coverage` reports total checkpoints, supported checkpoints, embedding count, and coverage percentage.
- The user can set the selected checkpoint as the current start location.

## Data Coverage

- The seed database contains one `recognition_refs` row per checkpoint.
- Supported recognition coverage is limited to checkpoints with pano assets.
- Current generated coverage: 59 supported checkpoints out of 75 total checkpoints.
- Android local fallback now scores multiple query crops, matching the backend's multi-view query strategy more closely.
- Shared recognition feature extraction lives in `python-common/campusvista_recognition` so backend inference and data generation use one package instead of importing app internals.

## Regeneration Commands

```bash
python3 python-tools/scripts/generate_seed_db.py
python3 python-tools/scripts/benchmark_recognition.py
python3 python-tools/scripts/benchmark_recognition.py --queries-dir python-tools/data/validation/phone_photos
```

## Active Pretrained Backend Encoder

The backend recognition index is now generated with OpenCLIP:

```text
campusvista-vpr-openclip-ViT-B-32-laion2b_s34b_b79k
```

This gives the backend a stronger pretrained visual encoder than the
handcrafted baseline.

Regenerate the OpenCLIP backend index with:

```bash
CAMPUSVISTA_RECOGNITION_ENCODER=openclip python3 python-tools/scripts/build_recognition_index.py
```

OpenCLIP indexes are backend-only. Android local fallback intentionally
continues to use the handcrafted `assets/ml/recognition_index.bin` format
because the Android runtime does not ship an OpenCLIP/TFLite encoder yet.

The handcrafted generator still exists for Android fallback and low-dependency
rebuilds.

## Accuracy Notes

The benchmark script reports reference-pano smoke accuracy. It does not prove real phone-photo accuracy. To honestly claim an 8/10 field result, collect a separate phone-photo test set and report top-1, top-3, rejection rate, and latency.
