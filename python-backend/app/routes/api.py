from __future__ import annotations

from fastapi import APIRouter, HTTPException, Query

from app.models import (
    CheckpointOut,
    MapConfigOut,
    NearestCheckpointOut,
    PanoOut,
    PlaceOut,
    RecognitionRequest,
    RecognitionResponse,
    RouteRequest,
    RouteResponse,
    SearchResultOut,
)
from app.services.pano_service import PanoService
from app.services.recognition_service import RecognitionService
from app.services.routing_service import RoutingService
from app.services.search_service import SearchService


router = APIRouter()


@router.get("/checkpoints", response_model=list[CheckpointOut], tags=["checkpoints"])
def get_checkpoints() -> list[dict]:
    return RoutingService().get_checkpoints()


@router.get("/checkpoints/nearest", response_model=NearestCheckpointOut, tags=["checkpoints"])
def get_nearest_checkpoint(
    x: float = Query(...),
    y: float = Query(...),
) -> dict:
    return RoutingService().nearest_checkpoint(x, y)


@router.get("/checkpoints/{checkpoint_id}", response_model=CheckpointOut, tags=["checkpoints"])
def get_checkpoint(checkpoint_id: str) -> dict:
    checkpoint = RoutingService().get_checkpoint(checkpoint_id)
    if checkpoint is None:
        raise HTTPException(status_code=404, detail="Checkpoint not found")
    return checkpoint


@router.get("/map/config", response_model=MapConfigOut, tags=["map"])
def get_map_config() -> dict:
    return RoutingService().get_map_config()


@router.get("/places/search", response_model=list[SearchResultOut], tags=["search"])
def search_places(
    q: str = Query(default=""),
    place_type: str | None = Query(default=None),
    limit: int = Query(default=10, ge=1, le=50),
) -> list[dict]:
    return SearchService().search(q, place_type=place_type, limit=limit)


@router.get("/places/{place_id}", response_model=PlaceOut, tags=["search"])
def get_place(place_id: str) -> dict:
    place = SearchService().get_place(place_id)
    if place is None:
        raise HTTPException(status_code=404, detail="Place not found")
    return place


@router.get("/panos/{checkpoint_id}", response_model=PanoOut, tags=["panos"])
def get_pano(checkpoint_id: str) -> dict:
    pano = PanoService().get_pano_for_checkpoint(checkpoint_id)
    if pano is None:
        raise HTTPException(status_code=404, detail="Outdoor pano not found")
    return pano


@router.post("/route", response_model=RouteResponse, tags=["routing"])
def build_route(request: RouteRequest) -> dict:
    try:
        return RoutingService().compute_route(request)
    except ValueError as exception:
        raise HTTPException(status_code=400, detail=str(exception)) from exception


@router.get("/recognition/refs", tags=["recognition"])
def get_recognition_refs() -> list[dict]:
    return RecognitionService().get_reference_labels()


@router.post("/recognize", response_model=RecognitionResponse, tags=["recognition"])
def recognize_location(request: RecognitionRequest) -> dict:
    return RecognitionService().recognize(request.model_dump())
