# CampusVista

CampusVista is a campus navigation MVP with a Python-heavy architecture:

```text
Android app -> FastAPI backend -> SQLite / JSON / assets
```

Android is the Java/XML frontend. It handles splash, map display, search UI, location selection, route screens, panorama display, and API calls. The Python FastAPI backend is the primary intelligence layer for database access, search, fuzzy matching, graph routing, nearest-checkpoint snapping, instruction generation, map metadata, panorama metadata, and crowd warnings.

The older Android SQLite/repository/routing code remains in the app only as a minimal fallback for demos when the backend is unavailable.

## Demo Runtime

Fastest local demo on macOS:

```text
Double-click: Run CampusVista.command
```

That launcher starts the OpenCLIP FastAPI backend, opens Android Studio if no
emulator/device is connected, and auto-installs/launches the app when an
emulator/device is already running.

For emulator demos, Android calls:

```text
http://10.0.2.2:8000
```

For a real phone on the same Wi-Fi network, set the Android backend URL to:

```text
http://<laptop-ip>:8000
```

The emulator path is the recommended demo path. The default debug build uses `10.0.2.2`. For a physical phone demo, rebuild with your laptop IP:

```powershell
cd android-app
.\gradlew.bat :app:assembleDebug -PcampusVistaBackendUrl=http://192.168.x.x:8000/
```

Android cleartext HTTP is enabled for this demo build in `AndroidManifest.xml`, so `http://` works as long as the phone can reach the laptop and Windows firewall allows port `8000`.

This is not a standalone offline Android app unless the Python backend is also running locally or bundled later.

## MVP Features

- Python FastAPI API for checkpoints, places, routing, panos, and photo recognition
- SQLite-backed campus seed data
- Excel-driven real-data import from `campus_data.xlsx`
- PDF map extraction with numbered checkpoint coordinate extraction
- Fuzzy place search with aliases
- A* routing with Dijkstra fallback
- Shortest-path routing with active crowd warning popups
- Nearest-checkpoint snapping from map coordinates
- Generated outdoor walking instructions
- Hybrid photo recognition from camera/gallery images against pano-backed checkpoint references
- Java/XML Android screens using Retrofit as the primary data path
- Android local fallback for search, checkpoints, routing, and panos

## API

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
|   |   |-- network/        # Retrofit client, backend DTOs, backend mappers
|   |   |-- ui/             # MVP screens
|   |   |-- data/           # local fallback repositories
|   |   `-- routing/        # local fallback routing
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

The team now only needs to provide the workbook, map PDF, and pano ZIP:

```powershell
python-tools\scripts\generate_seed_db.py `
  --workbook C:\Users\rohan\Downloads\campus_data.xlsx `
  --map-pdf C:\Users\rohan\Downloads\campus_map.pdf `
  --panos-zip C:\Users\rohan\Downloads\outdoor_panos.zip
```

The importer extracts `campus_map.png`, reads checkpoint markers `01` to `75`, fills missing coordinates and edge distances, normalizes available `.jpeg` panos to `.jpg`, generates aliases, rebuilds SQLite, and publishes backend plus Android assets.

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

For a physical phone, install an APK built with `-PcampusVistaBackendUrl=http://<laptop-ip>:8000/`.

## Demo Checklist

Before the demo:

- Start the FastAPI backend and confirm `/health`.
- Confirm search works with `cafeteria`.
- Confirm route generation from `OUT_CP001` to `PL_002`.
- Confirm photo recognition with a supported pano image.
- Build `:app:assembleDebug` successfully.
- Launch the Android emulator before presenting.
- Open CampusVista and verify the splash screen reaches the home map.
- See `docs/recognition_implementation.md` for recognition phase status and accuracy limits.

Suggested live flow:

- Show the Python backend terminal running.
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

- Backend not running: Android should fall back for routing/search, but photo recognition will show a backend-needed message. Start backend first.
- Emulator cannot connect: use `10.0.2.2:8000`, not `localhost:8000`, inside Android.
- Physical phone cannot connect: phone and laptop must be on the same network, Windows firewall may need to allow port `8000`, and the app backend URL must point to the laptop IP.
- Port already in use: stop the other process or run FastAPI on another port and update Android before rebuilding.
- Missing Python packages: activate `.venv` and run `pip install -r requirements.txt`.
- Missing seed data: verify `python-backend/data/campus_seed.db` exists.

## Build And Test

```bash
python -m unittest discover -s python-backend/tests
python -m unittest discover -s python-tools/tests
cd android-app
gradlew.bat :app:assembleDebug
```

## MVP Boundaries

Included now: outdoor navigation, map-based location selection, search, route preview/options, outdoor nav, and panorama lookup/display.

Not included yet: indoor navigation, live crowd feeds, GPS turn-by-turn, admin tools, and packaged on-device Python.
