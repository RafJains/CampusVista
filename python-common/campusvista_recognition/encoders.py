from __future__ import annotations

import os
from dataclasses import dataclass
from functools import lru_cache
from typing import Iterable, Protocol

import numpy as np
from PIL import Image

from . import features


HANDCRAFTED_ENCODER_NAME = "handcrafted"
OPENCLIP_ENCODER_NAME = "openclip"
DEFAULT_ENCODER_NAME = HANDCRAFTED_ENCODER_NAME
ENCODER_ENV = "CAMPUSVISTA_RECOGNITION_ENCODER"
OPENCLIP_MODEL_ENV = "CAMPUSVISTA_OPENCLIP_MODEL"
OPENCLIP_PRETRAINED_ENV = "CAMPUSVISTA_OPENCLIP_PRETRAINED"
OPENCLIP_CONFIDENCE_FLOOR_ENV = "CAMPUSVISTA_OPENCLIP_CONFIDENCE_FLOOR"
OPENCLIP_CONFIDENCE_SPAN_ENV = "CAMPUSVISTA_OPENCLIP_CONFIDENCE_SPAN"
OPENCLIP_DEFAULT_MODEL = "ViT-B-32"
OPENCLIP_DEFAULT_PRETRAINED = "laion2b_s34b_b79k"
OPENCLIP_DEFAULT_CONFIDENCE_FLOOR = 0.93
OPENCLIP_DEFAULT_CONFIDENCE_SPAN = 0.11


class VisualEncoder(Protocol):
    name: str
    model_version: str
    embedding_dimension: int
    confidence_floor: float
    confidence_span: float

    def embeddings_for_views(self, views: Iterable[Image.Image]) -> np.ndarray:
        ...


@dataclass(frozen=True)
class HandcraftedEncoder:
    name: str = HANDCRAFTED_ENCODER_NAME
    model_version: str = features.MODEL_VERSION
    embedding_dimension: int = features.EMBEDDING_DIMENSION
    confidence_floor: float = 0.90
    confidence_span: float = 0.14

    def embeddings_for_views(self, views: Iterable[Image.Image]) -> np.ndarray:
        embeddings = [features.extract_embedding(view) for view in views]
        if not embeddings:
            return np.empty((0, self.embedding_dimension), dtype=np.float32)
        return np.vstack(embeddings).astype(np.float32)


class OpenClipEncoder:
    name = OPENCLIP_ENCODER_NAME

    def __init__(
        self,
        model_name: str = OPENCLIP_DEFAULT_MODEL,
        pretrained: str = OPENCLIP_DEFAULT_PRETRAINED,
        confidence_floor: float = OPENCLIP_DEFAULT_CONFIDENCE_FLOOR,
        confidence_span: float = OPENCLIP_DEFAULT_CONFIDENCE_SPAN,
    ) -> None:
        try:
            import open_clip  # type: ignore[import-not-found]
            import torch  # type: ignore[import-not-found]
        except ImportError as exception:
            raise RuntimeError(
                "OpenCLIP recognition requires optional dependencies: torch and open_clip_torch."
            ) from exception

        self._torch = torch
        self._device = _best_torch_device(torch)
        self._model, _, self._preprocess = open_clip.create_model_and_transforms(
            model_name,
            pretrained=pretrained,
        )
        self._model.eval().to(self._device)
        self.model_name = model_name
        self.pretrained = pretrained
        self.model_version = f"campusvista-vpr-openclip-{model_name}-{pretrained}"
        self.embedding_dimension = self._infer_embedding_dimension()
        self.confidence_floor = confidence_floor
        self.confidence_span = confidence_span

    def embeddings_for_views(self, views: Iterable[Image.Image]) -> np.ndarray:
        tensors = [self._preprocess(view.convert("RGB")) for view in views]
        if not tensors:
            return np.empty((0, self.embedding_dimension), dtype=np.float32)

        with self._torch.no_grad():
            batch = self._torch.stack(tensors).to(self._device)
            features_tensor = self._model.encode_image(batch)
            features_tensor = features_tensor / features_tensor.norm(dim=-1, keepdim=True).clamp_min(1e-12)
            return features_tensor.cpu().numpy().astype(np.float32)

    def _infer_embedding_dimension(self) -> int:
        probe = Image.new("RGB", (features.IMAGE_SIZE, features.IMAGE_SIZE), "white")
        return int(self.embeddings_for_views([probe]).shape[1])


def selected_encoder_name() -> str:
    return os.environ.get(ENCODER_ENV, DEFAULT_ENCODER_NAME).strip().lower() or DEFAULT_ENCODER_NAME


def encoder_from_environment() -> VisualEncoder:
    return create_encoder(selected_encoder_name())


def create_encoder(name: str | None = None) -> VisualEncoder:
    encoder_name = (name or DEFAULT_ENCODER_NAME).strip().lower()
    if encoder_name == HANDCRAFTED_ENCODER_NAME:
        return _handcrafted_encoder()
    if encoder_name == OPENCLIP_ENCODER_NAME:
        return _openclip_encoder(
            os.environ.get(OPENCLIP_MODEL_ENV, OPENCLIP_DEFAULT_MODEL),
            os.environ.get(OPENCLIP_PRETRAINED_ENV, OPENCLIP_DEFAULT_PRETRAINED),
            float(os.environ.get(OPENCLIP_CONFIDENCE_FLOOR_ENV, OPENCLIP_DEFAULT_CONFIDENCE_FLOOR)),
            float(os.environ.get(OPENCLIP_CONFIDENCE_SPAN_ENV, OPENCLIP_DEFAULT_CONFIDENCE_SPAN)),
        )
    raise ValueError(f"Unsupported recognition encoder: {encoder_name}")


def encoder_for_model_version(model_version: str) -> VisualEncoder:
    if model_version == features.MODEL_VERSION:
        return HandcraftedEncoder()
    if model_version.startswith("campusvista-vpr-openclip-"):
        encoder = create_encoder(OPENCLIP_ENCODER_NAME)
        if encoder.model_version != model_version:
            raise ValueError(
                "OpenCLIP model configuration does not match the recognition index. "
                f"Expected {model_version}, got {encoder.model_version}."
            )
        return encoder
    raise ValueError(f"Unsupported recognition model version: {model_version}")


@lru_cache(maxsize=1)
def _handcrafted_encoder() -> HandcraftedEncoder:
    return HandcraftedEncoder()


@lru_cache(maxsize=4)
def _openclip_encoder(
    model_name: str,
    pretrained: str,
    confidence_floor: float,
    confidence_span: float,
) -> OpenClipEncoder:
    return OpenClipEncoder(
        model_name=model_name,
        pretrained=pretrained,
        confidence_floor=confidence_floor,
        confidence_span=confidence_span,
    )


def _best_torch_device(torch_module: object) -> str:
    if torch_module.cuda.is_available():
        return "cuda"
    mps = getattr(torch_module.backends, "mps", None)
    if mps is not None and mps.is_available():
        return "mps"
    return "cpu"
