from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path

import numpy as np
import torch
from PIL import Image

REPO_ROOT = Path(__file__).resolve().parents[2]
PYTHON_COMMON_DIR = REPO_ROOT / "python-common"
if str(PYTHON_COMMON_DIR) not in sys.path:
    sys.path.insert(0, str(PYTHON_COMMON_DIR))

from campusvista_recognition import decode_image, reference_views  # noqa: E402
from campusvista_recognition.encoders import (  # noqa: E402
    OPENCLIP_DEFAULT_MODEL,
    OPENCLIP_DEFAULT_PRETRAINED,
)


ANDROID_ML_DIR = REPO_ROOT / "android-app" / "app" / "src" / "main" / "assets" / "ml"
RAW_PANO_DIR = REPO_ROOT / "python-tools" / "data" / "raw" / "outdoor_panos"
RAW_PANO_CSV = REPO_ROOT / "python-tools" / "data" / "raw" / "outdoor_panos.csv"


class NormalizedImageEncoder(torch.nn.Module):
    def __init__(self, model: torch.nn.Module) -> None:
        super().__init__()
        self.model = model

    def forward(self, image: torch.Tensor) -> torch.Tensor:
        features = self.model.encode_image(image)
        return torch.nn.functional.normalize(features, dim=-1)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Export the OpenCLIP image encoder and matching Android recognition index."
    )
    parser.add_argument("--model", default=OPENCLIP_DEFAULT_MODEL)
    parser.add_argument("--pretrained", default=OPENCLIP_DEFAULT_PRETRAINED)
    parser.add_argument("--output-dir", type=Path, default=ANDROID_ML_DIR)
    parser.add_argument("--opset", type=int, default=17)
    parser.add_argument(
        "--keep-fp32",
        action="store_true",
        help="Keep the large FP32 ONNX export next to the quantized Android model.",
    )
    args = parser.parse_args()

    import open_clip  # type: ignore[import-not-found]

    args.output_dir.mkdir(parents=True, exist_ok=True)
    model, _, preprocess = open_clip.create_model_and_transforms(
        args.model,
        pretrained=args.pretrained,
    )
    model.eval()
    encoder = NormalizedImageEncoder(model).eval()

    onnx_path = args.output_dir / "openclip_image_encoder.onnx"
    fp32_path = args.output_dir / "openclip_image_encoder_fp32.onnx"
    probe = torch.randn(1, 3, 224, 224, dtype=torch.float32)
    torch.onnx.export(
        encoder,
        probe,
        fp32_path,
        input_names=["image"],
        output_names=["embedding"],
        dynamic_axes={"image": {0: "batch"}, "embedding": {0: "batch"}},
        opset_version=args.opset,
    )

    quantize_for_mobile(fp32_path, onnx_path)
    if not args.keep_fp32:
        fp32_path.unlink(missing_ok=True)

    import onnxruntime as ort  # type: ignore[import-not-found]

    session = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    input_name = session.get_inputs()[0].name
    embeddings: list[np.ndarray] = []
    labels: list[tuple[str, str]] = []
    for checkpoint_id, image_file in supported_panos():
        image = decode_image((RAW_PANO_DIR / image_file).read_bytes())
        batch = np.vstack([clip_input(view.convert("RGB")) for view in reference_views(image)])
        features = session.run(None, {input_name: batch})[0].astype(np.float32)
        features /= np.maximum(np.linalg.norm(features, axis=1, keepdims=True), 1e-12)
        for embedding in features:
            embeddings.append(embedding)
            labels.append((checkpoint_id, image_file))

    matrix = np.vstack(embeddings).astype("<f4")
    index_path = args.output_dir / "openclip_recognition_index.bin"
    with index_path.open("wb") as file:
        np.array([matrix.shape[0], matrix.shape[1]], dtype="<i4").tofile(file)
        matrix.tofile(file)

    labels_path = args.output_dir / "openclip_recognition_index_labels.csv"
    with labels_path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.writer(file)
        writer.writerow(["checkpoint_id", "image_file"])
        writer.writerows(labels)

    print("Mobile OpenCLIP assets written:")
    print(f"- model: {onnx_path}")
    print(f"- index: {index_path}")
    print(f"- labels: {labels_path}")
    print(f"- embeddings: {matrix.shape[0]} x {matrix.shape[1]}")


def quantize_for_mobile(fp32_path: Path, output_path: Path) -> None:
    from onnxruntime.quantization import QuantType, quantize_dynamic  # type: ignore[import-not-found]

    temp_path = output_path.with_name(output_path.stem + "_quant_tmp.onnx")
    quantize_dynamic(
        str(fp32_path),
        str(temp_path),
        weight_type=QuantType.QInt8,
        op_types_to_quantize=["MatMul", "Gemm"],
    )
    temp_path.replace(output_path)


def clip_input(image: Image.Image) -> np.ndarray:
    prepared = center_crop(image.convert("RGB"))
    rgb = np.asarray(prepared, dtype=np.float32) / 255.0
    mean = np.array([0.48145466, 0.4578275, 0.40821073], dtype=np.float32)
    std = np.array([0.26862954, 0.26130258, 0.27577711], dtype=np.float32)
    normalized = (rgb - mean) / std
    return np.transpose(normalized, (2, 0, 1))[None, :, :, :].astype(np.float32)


def center_crop(image: Image.Image) -> Image.Image:
    width, height = image.size
    scale = max(224 / width, 224 / height)
    scaled_width = round(width * scale)
    scaled_height = round(height * scale)
    resized = image.resize((scaled_width, scaled_height), Image.Resampling.BILINEAR)
    x0 = max(0, (scaled_width - 224) // 2)
    y0 = max(0, (scaled_height - 224) // 2)
    return resized.crop((x0, y0, x0 + 224, y0 + 224))


def supported_panos() -> list[tuple[str, str]]:
    rows: list[tuple[str, str]] = []
    with RAW_PANO_CSV.open("r", encoding="utf-8", newline="") as file:
        reader = csv.DictReader(file)
        for row in reader:
            checkpoint_id = (row.get("checkpoint_id") or "").strip()
            image_file = (row.get("image_file") or "").strip()
            if checkpoint_id and image_file and (RAW_PANO_DIR / image_file).exists():
                rows.append((checkpoint_id, image_file))
    return rows


if __name__ == "__main__":
    main()
