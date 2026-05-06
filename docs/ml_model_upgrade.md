# CampusVista ML Model Upgrade Path

The current v1 recognition stack is a deterministic visual retrieval baseline.
It is intentionally lightweight and reproducible from existing pano assets.

The backend now uses an OpenCLIP retrieval index as the no-training accuracy
upgrade. It does not replace the Android fallback until a mobile TFLite encoder
is produced.

For a stronger production model:

1. Collect labeled, opt-in phone photos per supported checkpoint.
2. Split by capture session, not by random image, so validation measures real
   generalization.
3. Train an embedding model with a mobile backbone such as MobileNetV3,
   EfficientNet-Lite, or a compact ViT.
4. Use metric learning or classifier pretraining plus embedding export.
5. Export TFLite with float16 or int8 quantization.
6. Replace `assets/ml/recognition_index.bin` only after benchmarked top-1,
   top-3, rejection rate, and Android latency improve on real photos.

Do not claim 8/10 real-world accuracy until the phone-photo benchmark in
`python-tools/data/validation/phone_photos` has enough held-out examples.
