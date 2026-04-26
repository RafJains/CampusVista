from __future__ import annotations

from fastapi import APIRouter, HTTPException, Query

from app.models import CheckpointOut, MapConfigOut, NearestCheckpointOut, PanoOut
from app.services.pano_service import PanoService
from app.services.routing_service import RoutingService


router = APIRouter(tags=["checkpoints"])


@router.get("/checkpoints", response_model=list[CheckpointOut])
def get_checkpoints() -> list[dict]:
    return RoutingService().get_checkpoints()


@router.get("/checkpoints/nearest", response_model=NearestCheckpointOut)
def get_nearest_checkpoint(
    x: float = Query(...),
    y: float = Query(...),
) -> dict:
    return RoutingService().nearest_checkpoint(x, y)


@router.get("/checkpoints/{checkpoint_id}", response_model=CheckpointOut)
def get_checkpoint(checkpoint_id: str) -> dict:
    checkpoint = RoutingService().get_checkpoint(checkpoint_id)
    if checkpoint is None:
        raise HTTPException(status_code=404, detail="Checkpoint not found")
    return checkpoint


@router.get("/map/config", response_model=MapConfigOut)
def get_map_config() -> dict:
    return RoutingService().get_map_config()


@router.get("/panos/{checkpoint_id}", response_model=PanoOut)
def get_pano(checkpoint_id: str) -> dict:
    pano = PanoService().get_pano_for_checkpoint(checkpoint_id)
    if pano is None:
        raise HTTPException(status_code=404, detail="Outdoor pano not found")
    return pano
