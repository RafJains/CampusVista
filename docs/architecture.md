# CampusVista Architecture

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
