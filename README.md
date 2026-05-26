# CampusVista

CampusVista is a campus navigation MVP with an offline-native Android runtime:

```text
Android app -> packaged SQLite / JSON / image assets / on-device recognition
```

Android is the Java/XML app. It handles splash, map display, search UI, location selection, route screens, panorama display, local data access, search, graph routing, nearest-checkpoint snapping, instruction generation, map metadata, panorama metadata, crowd warnings, and on-device photo recognition.

The Python FastAPI backend remains in the repo as a development oracle and data/model generation tool. It is no longer required for normal installed Android app functionality.

## Public Repository Data Policy

This public repository intentionally excludes private campus data and generated
runtime assets. The code, UI, backend, algorithms, docs, and generation scripts
are visible, but raw CSV entries, edge data, maps, pano images, SQLite databases,
recognition indexes, ONNX models, APK/AAB outputs, and generated PDFs are not
tracked.

To run the full app locally, provide your own private data and regenerate the
assets with the Python tooling. Do not commit generated data or media back into
the public repo.

## Demo Runtime

Fastest local demo on macOS:

```text
Double-click: Run CampusVista.command
```

That launcher opens Android Studio if no emulator/device is connected, and
auto-installs/launches the app when an emulator/device is already running. The
installed app uses packaged offline campus data and does not need Wi-Fi, USB
tunneling, or a local backend after installation.

## MVP Features

- Offline Android engine for checkpoints, places, routing, panos, and photo recognition
- SQLite-backed campus seed data
- Excel-driven real-data import from `campus_data.xlsx`
- PDF map extraction with numbered checkpoint coordinate extraction
- Fuzzy place search with aliases
- A* routing with Dijkstra fallback
- Shortest-path routing with active crowd warning popups
- Nearest-checkpoint snapping from map coordinates
- Generated outdoor walking instructions
- Photo recognition UI with local/fallback matching against pano-backed checkpoint references
- No ONNX Runtime dependency is packaged in the default Android build
- Java/XML Android screens using packaged data as the primary data path

## API

These endpoints are provided by the Python development backend. They are useful
for parity checks and tooling, but the installed Android release app does not
call them for normal functionality.

- `GET /health`
- `GET /checkpoints`
- `GET /checkpoints/{checkpoint_id}`
- `GET /checkpoints/nearest?x=100&y=900`
- `GET /map/config`
- `GET /places/search?q=cafeteria`
- `GET /places/{place_id}`
- `GET /panos/{checkpoint_id}`
- `GET /recognition/refs`
- `GET /recognition/coverage`
- `POST /route`
- `POST /recognize` with multipart field `image`

Example route request:

```json
{
  "start_checkpoint_id": "OUT_CP001",
  "destination_query": "cafeteria",
  "route_mode": "shortest"
}
```

Example route response:

```json
{
  "route_found": true,
  "algorithm": "astar",
  "route_mode": "shortest",
  "total_distance": 156,
  "estimated_time": "3 min",
  "checkpoint_ids": ["OUT_CP001", "OUT_CP002", "OUT_CP003", "OUT_CP004", "OUT_CP038", "OUT_CP039", "OUT_CP040"],
  "instructions": [
    "Start walking toward Junction 3_5.",
    "Continue toward Reception Entry.",
    "Continue toward Admission Cell Entry.",
    "Continue toward ABB-3 Entry_2.",
    "Continue toward ABB-3 Entry_3.",
    "You have arrived at Cafeteria."
  ]
}
```

## Simplified Structure

```text
CampusVista/
|-- android-app/
|   |-- app/src/main/java/com/example/campusvista/
|   |   |-- engine/         # Android-native application engine
|   |   |-- ui/             # MVP screens
|   |   |-- data/           # local repositories and seed DB access
|   |   |-- routing/        # local graph routing
|   |   `-- recognition/    # local/OpenCLIP mobile recognition
|   `-- app/src/main/res/
|-- python-backend/
|   |-- app/
|   |   |-- main.py
|   |   |-- db.py
|   |   |-- models.py
|   |   |-- routes/api.py   # all MVP API endpoints
|   |   |-- services/       # search, routing, crowd, panos, recognition
|   |   `-- utils/
|   |-- data/              # private/generated locally, not tracked publicly
|   |-- tests/
|   `-- requirements.txt
|-- python-tools/           # seed DB generation and validation scripts
`-- docs/
```

## Run Backend For Development Parity

```bash
cd python-backend
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

This backend is optional for Android use. Keep it open only when validating the
Python oracle endpoints or regenerating fixtures.

Quick backend smoke checks from another PowerShell window:

```powershell
Invoke-RestMethod http://127.0.0.1:8000/health
Invoke-RestMethod "http://127.0.0.1:8000/places/search?q=cafeteria"
$body = @{
  start_checkpoint_id = "OUT_CP001"
  destination_place_id = "PL_002"
  route_mode = "shortest"
} | ConvertTo-Json
Invoke-RestMethod http://127.0.0.1:8000/route -Method Post -ContentType "application/json" -Body $body
```

The route smoke check should return `route_found: true`, `algorithm: astar`, and instructions ending with arrival at Cafeteria.

## Import Real Data

The public repo does not include source campus data. To run full functionality,
provide your own workbook, map PDF, and pano ZIP:

```powershell
python-tools\scripts\generate_seed_db.py `
  --workbook C:\Users\rohan\Downloads\campus_data.xlsx `
  --map-pdf C:\Users\rohan\Downloads\campus_map.pdf `
  --panos-zip C:\Users\rohan\Downloads\outdoor_panos.zip
```

The importer extracts `campus_map.png`, reads checkpoint markers, fills missing
coordinates and edge distances, normalizes available `.jpeg` panos to `.jpg`,
generates aliases, rebuilds SQLite, and publishes backend plus Android assets.

## Run Android For Demo

```bash
cd android-app
gradlew.bat :app:assembleDebug
```

Then run the app from Android Studio or install:

```bash
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

No backend is required before opening the installed Android app.

## Demo Checklist

Before the demo:

- Confirm search works with `cafeteria` in airplane mode.
- Confirm route generation from `OUT_CP001` to Cafeteria in airplane mode.
- Confirm photo recognition with a supported pano image.
- Build `:app:assembleDebug` successfully.
- Launch the Android emulator before presenting.
- Open CampusVista and verify the splash screen reaches the home map.
- See `docs/recognition_implementation.md` for recognition phase status and accuracy limits.

Suggested live flow:

- Open the Android app.
- Pick or keep the current outdoor location.
- Use Recognize location with a camera/gallery image and select the top checkpoint match.
- Search for `cafeteria`.
- Open Cafeteria details.
- Preview a shortest route.
- Show the crowd notice if the selected route is active during a crowd-rule time window.
- Start outdoor navigation.
- Open the panorama view for a checkpoint with pano data.

## Demo Failure Notes

- Public checkout missing data/assets: regenerate private assets locally before
  expecting the full Android runtime or backend parity endpoints to work.
- On-device recognition weak: verify that local recognition reference assets are generated, or use the manual checkpoint fallback during the demo.
- Missing seed data: verify private generated `python-backend/data/campus_seed.db`
  exists locally.

## Build And Test

```bash
python -m compileall python-backend python-tools python-common
cd android-app
gradlew.bat :app:assembleDebug
```

Asset-dependent backend/data-pipeline tests are intended for private local
checkouts where generated campus data is present.

## MVP Boundaries

Included now: outdoor navigation, map-based location selection, search, route preview/options, outdoor nav, and panorama lookup/display.

Not included yet: indoor navigation, live crowd feeds, GPS turn-by-turn, admin tools, and packaged on-device Python.
