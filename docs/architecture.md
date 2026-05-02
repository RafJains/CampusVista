<<<<<<< ours
<<<<<<< ours
﻿# CampusVista Architecture

CampusVista is moving from an Android-heavy offline app to a Python-heavy client/server MVP.

```text
Android App
  -> FastAPI request
Python Backend
  -> SQLite / JSON / CSV
Python Services
  -> route/search/recognition/pano responses
Android App
  -> displays returned results
```

## Responsibilities

Android owns UI and device interaction:

- splash screen
- home/map screen
- search input
- current location selection
- route display
- outdoor navigation screen
- pano image display
- camera capture
- Retrofit calls to the Python backend

Python owns the intelligence layer:

- SQLite database access
- checkpoint/place search
- fuzzy search
- graph building
- A* routing
- Dijkstra fallback
- crowd-aware route costs
- nearest checkpoint snapping
- instruction generation
- route validation
- map coordinate calculations
- panorama metadata lookup
- recognition-ready placeholder and future ML integration

## Runtime

Demo backend:

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Android emulator backend URL:

```text
http://10.0.2.2:8000
```

Real phone backend URL:

```text
http://<laptop-ip>:8000
```

The app is no longer a true standalone offline Android app unless the Python server is also available locally.

## Android Integration Status

The Android app now calls the backend first for:

- place search
- place and checkpoint lookup
- route calculation
- outdoor navigation route refresh
- nearest checkpoint snapping from map/list coordinates
- outdoor pano metadata
- recognition placeholder flow

If the backend is down, Android falls back to the existing local repositories and Java routing code.
=======
=======
>>>>>>> theirs
# CampusVista Architecture (Final)

## Runtime Architecture: Offline-first
All runtime features execute locally on Android:
- Routing
- Search
- Map rendering
- Route preview and guidance
- SQLite data reads
- Crowd penalty calculation
- Outdoor recognition inference
- Outdoor panorama viewing

No runtime dependency on internet, Python, FastAPI, or server APIs.

## Android Runtime Responsibilities
- UI and activity flow
- 2D map rendering and marker overlays
- Start location selection (map/search/camera)
- Local SQLite access via repositories
- A* primary routing and Dijkstra fallback
- Dynamic crowd-cost evaluation from static rules + device clock
- Instruction generation from route geometry and metadata
- TFLite inference and confidence handling
- Route and recognition fallback UX

## Python Tooling Responsibilities (Non-runtime)
- Raw data cleaning and normalization
- Validation (IDs, connectivity, map scale, crowd rules, file naming, labels)
- Processed JSON/CSV generation
- `campus_seed.db` generation
- Pano preparation/compression checks
- Recognition dataset preparation + model training + `.tflite` export

Python is tooling only and is never part of Android runtime.

## Graph + Cost Model
- Static graph (checkpoints + edges) is built once and cached.
- Crowd penalties are evaluated dynamically per route request.
- Cost formulas:
  - Shortest mode: `distance_meters`
  - Avoid-crowded mode: `distance_meters + crowdPenalty(to_checkpoint_id)`

## Heuristic & Map Config
A* heuristic is `pixel_distance × meters_per_pixel`.
- Python source of truth: `python-tools/config.json`
- Android runtime source: `android-app/app/src/main/assets/config/map_config.json`

Both must match after generation.
<<<<<<< ours
>>>>>>> theirs
=======
>>>>>>> theirs
