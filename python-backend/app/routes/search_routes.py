from __future__ import annotations

from fastapi import APIRouter, HTTPException, Query

from app.models import PlaceOut, SearchResultOut
from app.services.search_service import SearchService


router = APIRouter(tags=["search"])


@router.get("/places/search", response_model=list[SearchResultOut])
def search_places(
    q: str = Query(default=""),
    place_type: str | None = Query(default=None),
    limit: int = Query(default=10, ge=1, le=50),
) -> list[dict]:
    return SearchService().search(q, place_type=place_type, limit=limit)


@router.get("/places/{place_id}", response_model=PlaceOut)
def get_place(place_id: str) -> dict:
    place = SearchService().get_place(place_id)
    if place is None:
        raise HTTPException(status_code=404, detail="Place not found")
    return place
