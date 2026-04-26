from __future__ import annotations

from fastapi import APIRouter

from app.models import RecognitionRequest, RecognitionResponse
from app.services.recognition_service import RecognitionService


router = APIRouter(tags=["recognition"])


@router.get("/recognition/refs")
def get_recognition_refs() -> list[dict]:
    return RecognitionService().get_reference_labels()


@router.post("/recognize", response_model=RecognitionResponse)
def recognize_location(request: RecognitionRequest) -> dict:
    return RecognitionService().recognize(request.model_dump())
