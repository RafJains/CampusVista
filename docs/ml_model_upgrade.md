# CampusVista ML Model Upgrade Path

The current v1 recognition stack is a deterministic visual retrieval baseline.
It is intentionally lightweight and reproducible from existing pano assets.

The Android app now packages an ONNX Runtime Mobile OpenCLIP-style encoder and
a mobile-matched retrieval index as the no-training accuracy upgrade. The older
handcrafted Android index remains only as an emergency fallback.

For a stronger production model:

1. Collect labeled, opt-in phone photos per supported checkpoint.
2. Split by capture session, not by random image, so validation measures real
   generalization.
3. Compare the current ONNX OpenCLIP path against any smaller CLIP-family or
   mobile backbone candidates.
4. Use metric learning or classifier pretraining plus embedding export only if
   collected phone photos justify training.
5. Export through ONNX Runtime Mobile first; evaluate ExecuTorch if ONNX cannot
   meet latency, size, or fidelity requirements.
6. Replace `assets/ml/openclip_image_encoder.onnx` and
   `assets/ml/openclip_recognition_index.bin` only after benchmarked top-1,
   top-3, rejection rate, and Android latency improve on real photos.

Do not claim 8/10 real-world accuracy until the phone-photo benchmark in
`python-tools/data/validation/phone_photos` has enough held-out examples.
