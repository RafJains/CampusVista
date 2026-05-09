# Photo Recognition Implementation

CampusVista photo recognition is implemented as hybrid visual place recognition.

## Phase Status

- Phase 1, backend retrieval baseline: complete.
- Phase 2, mobile runtime: complete with ONNX Runtime Mobile and packaged OpenCLIP-style assets.
- Phase 3, accuracy improvement: benchmark tooling is complete, including a real-phone-photo validation mode. Real field accuracy claims still require a held-out phone-photo validation set.

## Runtime Flow

- Android lets the user take a photo or choose an image from gallery.
- The image is resized, JPEG-compressed, and passed to the local Android recognition engine.
- The primary Android path runs `assets/ml/openclip_image_encoder.onnx` with ONNX Runtime Mobile and compares the output against `assets/ml/openclip_recognition_index.bin`.
- If the ONNX model or OpenCLIP index cannot load, Android uses `assets/ml/recognition_index.bin` and `assets/ml/recognition_index_labels.csv` as an emergency handcrafted fallback.
- The Python backend `POST /recognize` and `GET /recognition/coverage` endpoints remain available for development parity and diagnostics.
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

Regenerate the Android OpenCLIP ONNX model and mobile-matched index with:

```bash
python3 python-tools/scripts/export_openclip_mobile.py
```

The handcrafted generator still exists for Android fallback and low-dependency
rebuilds.

## Accuracy Notes

The benchmark script reports reference-pano smoke accuracy. It does not prove real phone-photo accuracy. To honestly claim an 8/10 field result, collect a separate phone-photo test set and report top-1, top-3, rejection rate, and latency.
