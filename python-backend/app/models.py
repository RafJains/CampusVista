from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class CheckpointOut(BaseModel):
    checkpoint_id: str
    checkpoint_name: str
    checkpoint_type: str
    x_coord: float
    y_coord: float
    raw_map_x: float | None = None
    raw_map_y: float | None = None
    latitude: float | None = None
    longitude: float | None = None
    description: str | None = None
    orientation: str | None = None


class PlaceOut(BaseModel):
    place_id: str
    place_name: str
    place_type: str
    checkpoint_id: str
    description: str | None = None
    keywords: str | None = None


class SearchResultOut(PlaceOut):
    match_score: float
    match_source: str
    matched_text: str


class EdgeOut(BaseModel):
    edge_id: str
    from_checkpoint_id: str
    to_checkpoint_id: str
    distance_meters: float
    edge_type: str | None = None
    reverse_of_bidirectional_edge: bool = False


class PanoOut(BaseModel):
    pano_id: str
    checkpoint_id: str
    image_file: str
    thumbnail_file: str | None = None
    orientation: str | None = None
    description: str | None = None
    image_url: str | None = None
    thumbnail_url: str | None = None


class MapConfigOut(BaseModel):
    campus_map_file: str
    campus_map_width_px: int
    campus_map_height_px: int
    meters_per_pixel: float


class NearestCheckpointOut(BaseModel):
    checkpoint: CheckpointOut
    distance_pixels: float
    distance_meters: float


class RouteRequest(BaseModel):
    start_checkpoint_id: str | None = None
    start_x: float | None = None
    start_y: float | None = None
    destination_checkpoint_id: str | None = None
    destination_place_id: str | None = None
    destination_query: str | None = None
    destination_x: float | None = None
    destination_y: float | None = None
    route_mode: str = Field(default="shortest")
    now_iso: str | None = Field(default=None)


class RouteResponse(BaseModel):
    route_found: bool
    algorithm: str
    route_mode: str
    start_checkpoint_id: str | None = None
    destination_checkpoint_id: str | None = None
    destination_name: str | None = None
    total_distance: float = 0.0
    total_cost: float = 0.0
    estimated_time: str = "0 min"
    checkpoint_ids: list[str] = Field(default_factory=list)
    checkpoints: list[CheckpointOut] = Field(default_factory=list)
    edges: list[EdgeOut] = Field(default_factory=list)
    panos: list[PanoOut | None] = Field(default_factory=list)
    instructions: list[str] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)


class RecognitionRequest(BaseModel):
    image_base64: str | None = None
    label_name: str | None = None
    model_label_index: int | None = None
    confidence: float | None = None


class RecognitionCandidateOut(BaseModel):
    checkpoint_id: str
    label_name: str
    model_label_index: int
    confidence_threshold: float
    confidence: float | None = None


class RecognitionResponse(BaseModel):
    available: bool
    status: str
    message: str
    checkpoint_id: str | None = None
    confidence: float | None = None
    candidates: list[RecognitionCandidateOut] = Field(default_factory=list)
    fallback_options: list[str] = Field(default_factory=list)
    raw: dict[str, Any] = Field(default_factory=dict)
