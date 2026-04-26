# CampusVista

CampusVista is now a Python-heavy campus navigation system.

```text
Android app = Java/XML mobile frontend
Python backend = search, routing, data access, graph intelligence, and recognition-ready logic
```

The Android app remains the user interface for splash, home, map, search input, current location selection, route display, outdoor navigation, pano display, and camera capture. The Python FastAPI backend is the primary intelligence layer for SQLite access, fuzzy search, A* routing, Dijkstra fallback, crowd-aware route cost, nearest-checkpoint snapping, instruction generation, route validation, map coordinate calculations, panorama metadata, data cleaning, seed DB generation, and recognition placeholders.

The previous Android offline routing code is intentionally kept as fallback/reference during the transition.

## Runtime Architecture

```text
Android App
   |
   | HTTP API request
   v
Python FastAPI Backend
   |
   | sqlite3 / JSON / CSV
   v
CampusVista data
```

For emulator demos, Android should call:

```text
http://10.0.2.2:8000
```

For a real phone on the same network, Android should call:

```text
http://<laptop-ip>:8000
```

This architecture is not true standalone offline Android unless the Python server is running locally or bundled on-device later.

## Current Features

- Java/XML Android MVP UI
- Python FastAPI backend foundation
- SQLite-backed checkpoint/place access
- Fuzzy place search with aliases
- A* outdoor routing
- Dijkstra fallback routing
- Avoid-crowded path cost mode
- Nearest-checkpoint snapping from map coordinates
- Generated route instructions
- Outdoor pano metadata lookup
- Recognition-ready placeholder API
- Python data validation and seed DB generation

## API Endpoints

- `GET /health`
- `GET /checkpoints`
- `GET /checkpoints/{checkpoint_id}`
- `GET /checkpoints/nearest?x=100&y=900`
- `GET /map/config`
- `GET /places/search?q=library`
- `GET /places/{place_id}`
- `GET /panos/{checkpoint_id}`
- `POST /route`
- `GET /recognition/refs`
- `POST /recognize`

Example route request:

```json
{
  "start_checkpoint_id": "OUT_CP001",
  "destination_query": "library",
  "route_mode": "avoid_crowded"
}
```

Example route response:

```json
{
  "route_found": true,
  "algorithm": "astar",
  "route_mode": "shortest",
  "total_distance": 191,
  "estimated_time": "3 min",
  "checkpoint_ids": ["OUT_CP001", "OUT_CP002", "OUT_CP003", "OUT_CP004"],
  "instructions": [
    "Start walking toward Main Path Junction.",
    "Turn left toward Admin Block Entrance.",
    "Turn right toward Library Front.",
    "You have arrived at Library."
  ]
}
```

## Tech Stack

### Android

- Java
- XML layouts
- Retrofit planned for API calls
- Camera intent placeholder
- Local UI rendering
- Existing SQLite/routing fallback retained during transition

### Python Backend

- FastAPI
- Uvicorn
- SQLite via `sqlite3`
- Pydantic
- Standard-library graph/routing logic
- pandas/numpy reserved for future data/ML work
- OpenCV/TensorFlow/TFLite optional for future recognition

## Folder Structure

```text
CampusVista/
|-- android-app/
|   `-- app/src/main/
|       |-- java/com/example/campusvista/
|       |-- res/
|       `-- assets/
|-- python-backend/
|   |-- app/
|   |   |-- main.py
|   |   |-- db.py
|   |   |-- models.py
|   |   |-- routes/
|   |   |-- services/
|   |   `-- utils/
|   |-- data/
|   |   |-- campus_seed.db
|   |   |-- map_config.json
|   |   `-- pano/outdoor/
|   |-- tests/
|   |-- requirements.txt
|   `-- README.md
|-- python-tools/
|   |-- data/
|   |-- scripts/
|   `-- tests/
|-- docs/
`-- README.md
```

## Run Python Backend

```bash
cd python-backend
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

## Run Validation

```bash
python -m unittest discover -s python-backend/tests
python -m unittest discover -s python-tools/tests
```

Android build:

```bash
cd android-app
gradlew.bat :app:assembleDebug
```

## MVP Scope

Included:

- Outdoor checkpoints and route edges
- Python-backed search and routing
- Static crowd-aware routing
- Outdoor pano metadata and assets
- Recognition-ready backend placeholder
- Android frontend MVP screens

Not included yet:

- Android Retrofit API migration
- Production recognition model
- Indoor navigation
- GPS turn-by-turn navigation
- Real-time crowd tracking
- Admin dashboard

## Status

```text
Architecture pivot accepted
Python backend foundation implemented
Android frontend still uses local fallback logic
Next milestone: Android Retrofit API integration
```
