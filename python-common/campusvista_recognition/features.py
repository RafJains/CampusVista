from __future__ import annotations

from io import BytesIO

import numpy as np
from PIL import Image, ImageEnhance, ImageFilter, ImageOps, UnidentifiedImageError


MODEL_VERSION = "campusvista-vpr-histogram-v1"
EMBEDDING_DIMENSION = 734
IMAGE_SIZE = 224


class ImageDecodeError(ValueError):
    """Raised when supplied image bytes cannot be decoded safely."""


def decode_image(image_bytes: bytes) -> Image.Image:
    try:
        image = Image.open(BytesIO(image_bytes))
        image.load()
    except (OSError, UnidentifiedImageError) as exception:
        raise ImageDecodeError("Image could not be decoded.") from exception
    return ImageOps.exif_transpose(image).convert("RGB")


def reference_views(image: Image.Image) -> list[Image.Image]:
    source = image.convert("RGB")
    width, height = source.size
    crops: list[Image.Image] = [source]

    for ratio in (0.38, 0.5, 0.65):
        crop_width = max(1, int(width * ratio))
        crop_height = max(1, int(height * 0.86))
        y0 = max(0, (height - crop_height) // 2)
        steps = 8 if ratio == 0.38 else 6
        for index in range(steps):
            x0 = int((width - crop_width) * index / max(1, steps - 1))
            crops.append(source.crop((x0, y0, x0 + crop_width, y0 + crop_height)))

    variants: list[Image.Image] = []
    for crop in crops:
        variants.append(crop)
        if len(variants) < 27:
            variants.append(ImageEnhance.Brightness(crop).enhance(0.86))
        if len(variants) < 27:
            variants.append(ImageEnhance.Contrast(crop).enhance(1.14))
        if len(variants) >= 27:
            break
    return variants[:27]


def query_views(image: Image.Image) -> list[Image.Image]:
    source = image.convert("RGB")
    width, height = source.size
    crops = [source]
    seen = {(0, 0, width, height)}

    def add_crop(width_ratio: float, height_ratio: float, x_fraction: float, y_fraction: float) -> None:
        crop_width = max(1, int(width * width_ratio))
        crop_height = max(1, int(height * height_ratio))
        max_x = max(0, width - crop_width)
        max_y = max(0, height - crop_height)
        x0 = int(round(max_x * x_fraction))
        y0 = int(round(max_y * y_fraction))
        key = (x0, y0, crop_width, crop_height)
        if key in seen:
            return
        seen.add(key)
        crops.append(source.crop((x0, y0, x0 + crop_width, y0 + crop_height)))

    for x_fraction, y_fraction in (
        (0.5, 0.5),
        (0.0, 0.5),
        (1.0, 0.5),
        (0.5, 0.0),
        (0.5, 1.0),
        (0.0, 0.0),
        (1.0, 1.0),
    ):
        add_crop(0.78, 0.78, x_fraction, y_fraction)
    for x_fraction in (0.0, 0.5, 1.0):
        add_crop(0.62, 0.86, x_fraction, 0.5)
    return crops


def extract_embedding(image: Image.Image) -> np.ndarray:
    prepared = ImageOps.fit(image.convert("RGB"), (IMAGE_SIZE, IMAGE_SIZE), method=Image.Resampling.BILINEAR)
    rgb = np.asarray(prepared, dtype=np.float32) / 255.0
    hsv = np.asarray(prepared.convert("HSV"), dtype=np.float32)

    hist_parts = []
    for channel, bins, maximum in ((0, 8, 255), (1, 8, 255), (2, 8, 255)):
        hist, _ = np.histogram(hsv[:, :, channel], bins=bins, range=(0, maximum))
        hist_parts.append(hist.astype(np.float32))
    color_hist = np.concatenate(hist_parts)
    color_hist /= max(float(color_hist.sum()), 1.0)

    small = prepared.convert("L").resize((16, 16), Image.Resampling.BILINEAR)
    texture = np.asarray(small, dtype=np.float32) / 255.0
    texture = texture - float(texture.mean())

    blurred = prepared.convert("L").filter(ImageFilter.GaussianBlur(radius=1.0))
    gray = np.asarray(blurred, dtype=np.float32) / 255.0
    gradient_y, gradient_x = np.gradient(gray)
    magnitude = np.sqrt(gradient_x * gradient_x + gradient_y * gradient_y)
    angle = (np.arctan2(gradient_y, gradient_x) + np.pi) / (2 * np.pi)
    edge_hist, _ = np.histogram(angle, bins=16, range=(0.0, 1.0), weights=magnitude)
    edge_hist = edge_hist.astype(np.float32)
    edge_hist /= max(float(edge_hist.sum()), 1.0)

    channel_means = rgb.mean(axis=(0, 1))
    channel_stds = rgb.std(axis=(0, 1))
    feature = np.concatenate(
        [
            color_hist,
            texture.reshape(-1),
            edge_hist,
            channel_means.astype(np.float32),
            channel_stds.astype(np.float32),
            _spatial_color_grid(rgb),
        ]
    ).astype(np.float32)
    norm = float(np.linalg.norm(feature))
    if norm > 0:
        feature /= norm
    return feature


def _spatial_color_grid(rgb: np.ndarray) -> np.ndarray:
    grid_size = 12
    height, width, _ = rgb.shape
    cells: list[np.ndarray] = []
    for row in range(grid_size):
        y0 = int(height * row / grid_size)
        y1 = int(height * (row + 1) / grid_size)
        for col in range(grid_size):
            x0 = int(width * col / grid_size)
            x1 = int(width * (col + 1) / grid_size)
            cells.append(rgb[y0:y1, x0:x1, :].mean(axis=(0, 1)))
    return np.concatenate(cells).astype(np.float32)
