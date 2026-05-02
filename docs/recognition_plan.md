# CampusVista Recognition Plan (Final MVP)

## Scope
Recognition is **outdoor-only**.

## Runtime Flow (Android)
1. User taps **Use Camera to Detect Outdoor Location**.
2. Capture image.
3. Preprocess image locally.
4. Run TFLite inference locally.
5. Apply confidence policy.
6. Map label to checkpoint via `recognition_refs`.

## Confidence Policy
- `confidence >= 0.70`: auto-accept
- `0.50 <= confidence < 0.70`: show top suggestions for user confirmation
- `confidence < 0.50`: fallback flow

Fallback options: map selection, text search, retry camera.

## Label Validation (Python)
Validation must ensure:
- every `model_label_index` exists in `labels.txt`
- `label_name == labels.txt[model_label_index]`

On mismatch, seed/model packaging must fail.

## Pano vs Recognition Images
- `outdoor_panos.image_file`: runtime display panorama
- recognition reference images: Python-only training artifacts
- Android runtime ships only `.tflite` and `labels.txt`
