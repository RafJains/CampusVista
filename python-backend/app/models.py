from __future__ import annotations

from typing import Optional

from pydantic import BaseModel, Field


class CheckpointOut(BaseModel):
    checkpoint_id: str
    checkpoint_name: str
    checkpoint_type: str
    x_coord: float
    y_coord: float
    raw_map_x: Optional[float] = None
    raw_map_y: Optional[float] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    description: Optional[str] = None
    orientation: Optional[str] = None


class PlaceOut(BaseModel):
    place_id: str
    place_name: str
    place_type: str
    checkpoint_id: str
    description: Optional[str] = None
    keywords: Optional[str] = None


class SearchResultOut(PlaceOut):
    match_score: float
    match_source: str
    matched_text: str


class EdgeOut(BaseModel):
    edge_id: str
    from_checkpoint_id: str
    to_checkpoint_id: str
    distance_meters: float
    edge_type: Optional[str] = None
    reverse_of_bidirectional_edge: bool = False


class PanoOut(BaseModel):
    pano_id: str
    checkpoint_id: str
    image_file: str
    thumbnail_file: Optional[str] = None
    orientation: Optional[str] = None
    description: Optional[str] = None
    image_url: Optional[str] = None
    thumbnail_url: Optional[str] = None


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
    start_checkpoint_id: Optional[str] = None
    start_x: Optional[float] = None
    start_y: Optional[float] = None
    destination_checkpoint_id: Optional[str] = None
    destination_place_id: Optional[str] = None
    destination_query: Optional[str] = None
    destination_x: Optional[float] = None
    destination_y: Optional[float] = None
    route_mode: str = Field(default="shortest")
    now_iso: Optional[str] = Field(default=None)


class RouteResponse(BaseModel):
    route_found: bool
    algorithm: str
    route_mode: str
    start_checkpoint_id: Optional[str] = None
    destination_checkpoint_id: Optional[str] = None
    destination_name: Optional[str] = None
    total_distance: float = 0.0
    total_cost: float = 0.0
    estimated_time: str = "0 min"
    checkpoint_ids: list[str] = Field(default_factory=list)
    checkpoints: list[CheckpointOut] = Field(default_factory=list)
    edges: list[EdgeOut] = Field(default_factory=list)
    panos: list[Optional[PanoOut]] = Field(default_factory=list)
    instructions: list[str] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)


class RecognitionMatchOut(BaseModel):
    checkpoint_id: str
    checkpoint_name: str
    confidence_percent: float
    rank: int
    reference_image_url: Optional[str] = None
    supporting_views: int = 0


class RecognitionResponse(BaseModel):
    recognized: bool
    matches: list[RecognitionMatchOut] = Field(default_factory=list)
    message: str
    model_version: str
