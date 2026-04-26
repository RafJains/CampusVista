from __future__ import annotations

from fastapi import APIRouter, HTTPException

from app.models import RouteRequest, RouteResponse
from app.services.routing_service import RoutingService


router = APIRouter(tags=["routing"])


@router.post("/route", response_model=RouteResponse)
def build_route(request: RouteRequest) -> dict:
    try:
        return RoutingService().compute_route(request)
    except ValueError as exception:
        raise HTTPException(status_code=400, detail=str(exception)) from exception
