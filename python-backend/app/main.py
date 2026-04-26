from __future__ import annotations

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from app import db
from app.routes.api import router as api_router


app = FastAPI(
    title="CampusVista Python Backend",
    version="0.1.0",
    description=(
        "Python-heavy CampusVista backend for search, routing, crowd costs, "
        "instructions, pano metadata, and recognition-ready APIs."
    ),
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api_router)

if db.DATA_DIR.exists():
    app.mount("/assets", StaticFiles(directory=db.DATA_DIR), name="assets")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "campusvista-python-backend"}
