# CampusVista

CampusVista is a campus navigation MVP with a Python-heavy architecture:

```text
Android app -> FastAPI backend -> SQLite / JSON / assets
```

Android is the Java/XML frontend. It handles splash, map display, search UI, location selection, route screens, panorama display, camera capture, and API calls. The Python FastAPI backend is the primary intelligence layer for database access, search, fuzzy matching, graph routing, crowd-aware costs, nearest-checkpoint snapping, instruction generation, map metadata, panorama metadata, and the recognition-ready placeholder flow.

The older Android SQLite/repository/routing code remains in the app only as a minimal fallback for demos when the backend is unavailable.

## Demo Runtime

For emulator demos, Android calls:

```text
http://10.0.2.2:8000
```

For a real phone on the same Wi-Fi network, set the Android backend URL to:

```text
http://<laptop-ip>:8000
```

The emulator path is the recommended demo path. The current app default is configured in `BackendConfig` for `10.0.2.2`; for a physical phone demo, update the backend URL to your laptop IP and rebuild.

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

## Run Backend For Demo

```bash
cd python-backend
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Keep this terminal open during the Android demo.

Quick backend smoke checks from another PowerShell window:

```powershell
Invoke-RestMethod http://127.0.0.1:8000/health
Invoke-RestMethod "http://127.0.0.1:8000/places/search?q=library"
$body = @{
  start_checkpoint_id = "OUT_CP001"
  destination_place_id = "PL_LIBRARY"
  route_mode = "shortest"
} | ConvertTo-Json
Invoke-RestMethod http://127.0.0.1:8000/route -Method Post -ContentType "application/json" -Body $body
```

The route smoke check should return `route_found: true`, `algorithm: astar`, and instructions ending with arrival at Library.

## Run Android For Demo

```bash
cd android-app
gradlew.bat :app:assembleDebug
```

Then run the app from Android Studio or install:

```bash
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Start the backend before opening the app. On the Android emulator, Retrofit will reach the laptop backend through `http://10.0.2.2:8000/`.

## Demo Checklist

Before the demo:

- Start the FastAPI backend and confirm `/health`.
- Confirm search works with `library`.
- Confirm route generation from `OUT_CP001` to `PL_LIBRARY`.
- Build `:app:assembleDebug` successfully.
- Launch the Android emulator before presenting.
- Open CampusVista and verify the splash screen reaches the home map.

Suggested live flow:

- Show the Python backend terminal running.
- Open the Android app.
- Pick or keep the current outdoor location.
- Search for `library`.
- Open Library details.
- Preview a shortest route.
- Switch to avoid-crowded route if needed.
- Start outdoor navigation.
- Open the panorama view for a checkpoint with pano data.
- Show the camera recognition fallback and explain it is ML-ready placeholder behavior.

## Demo Failure Notes

- Backend not running: Android should fall back to local demo data, but Python-owned routing/search will not be demonstrated. Start backend first.
- Emulator cannot connect: use `10.0.2.2:8000`, not `localhost:8000`, inside Android.
- Physical phone cannot connect: phone and laptop must be on the same network, Windows firewall may need to allow port `8000`, and the app backend URL must point to the laptop IP.
- Port already in use: stop the other process or run FastAPI on another port and update Android before rebuilding.
- Missing Python packages: activate `.venv` and run `pip install -r requirements.txt`.
- Missing seed data: verify `python-backend/data/campus_seed.db` exists.
- Recognition expectations: the current recognition API is a placeholder/fallback flow, not a trained production ML model.

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
