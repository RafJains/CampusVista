# CampusVista

CampusVista is a campus navigation MVP with a Python-heavy architecture:

```text
Android app -> FastAPI backend -> SQLite / JSON / assets
```

Android is the Java/XML frontend. It handles splash, map display, search UI, location selection, route screens, panorama display, camera capture, and API calls. The Python FastAPI backend is the primary intelligence layer for database access, search, fuzzy matching, graph routing, crowd-aware costs, nearest-checkpoint snapping, instruction generation, map metadata, panorama metadata, and the recognition-ready placeholder flow.

The older Android SQLite/repository/routing code remains in the app only as a minimal fallback for demos when the backend is unavailable.

## Runtime

For emulator demos, Android calls:

```text
http://10.0.2.2:8000
```

For a real phone on the same Wi-Fi network, set the Android backend URL to:

```text
http://<laptop-ip>:8000
```

This is not a standalone offline Android app unless the Python backend is also running locally or bundled later.

## MVP Features

- Python FastAPI API for checkpoints, places, routing, panos, and recognition placeholders
- SQLite-backed campus seed data
- Fuzzy place search with aliases
- A* routing with Dijkstra fallback
- Crowd-aware route cost mode
- Nearest-checkpoint snapping from map coordinates
- Generated outdoor walking instructions
- Java/XML Android screens using Retrofit as the primary data path
- Android local fallback for search, checkpoints, routing, and panos

## API

- `GET /health`
- `GET /checkpoints`
- `GET /checkpoints/{checkpoint_id}`
- `GET /checkpoints/nearest?x=100&y=900`
- `GET /map/config`
- `GET /places/search?q=library`
- `GET /places/{place_id}`
- `GET /panos/{checkpoint_id}`
- `GET /recognition/refs`
- `POST /recognize`
- `POST /route`

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

## Simplified Structure

```text
CampusVista/
|-- android-app/
|   |-- app/src/main/java/com/example/campusvista/
|   |   |-- network/        # Retrofit client, backend DTOs, backend mappers
|   |   |-- ui/             # MVP screens
|   |   |-- data/           # local fallback repositories
|   |   |-- routing/        # local fallback routing
|   |   `-- recognition/    # camera/recognition fallback scaffolding
|   `-- app/src/main/res/
|-- python-backend/
|   |-- app/
|   |   |-- main.py
|   |   |-- db.py
|   |   |-- models.py
|   |   |-- routes/api.py   # all MVP API endpoints
|   |   |-- services/       # search, routing, crowd, panos, recognition
|   |   `-- utils/
|   |-- data/
|   |-- tests/
|   `-- requirements.txt
|-- python-tools/           # seed DB generation and validation
`-- docs/
```

## Run Backend

```bash
cd python-backend
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

## Build And Test

```bash
python -m unittest discover -s python-backend/tests
python -m unittest discover -s python-tools/tests
cd android-app
gradlew.bat :app:assembleDebug
```

## MVP Boundaries

Included now: outdoor navigation, map-based location selection, search, route preview/options, outdoor nav, panorama lookup/display, and recognition-ready fallback screens.

Not included yet: production ML recognition, indoor navigation, live crowd feeds, GPS turn-by-turn, admin tools, and packaged on-device Python.
