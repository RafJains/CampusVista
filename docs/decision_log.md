<<<<<<< ours
<<<<<<< ours
﻿# Decision Log

## Architecture Pivot

Decision:

CampusVista will use a Python-heavy architecture.

```text
Android = UI/display layer
Python = backend and intelligence layer
```

Reason:

The project now prioritizes Python/backend/AI work. Python is better suited for graph algorithms, fuzzy search, data validation, and future ML recognition.

Impact:

- FastAPI backend added under `python-backend/`
- Android Retrofit client added under `android-app/app/src/main/java/com/example/campusvista/network/`
- Android local routing remains as fallback/reference
- The system is no longer standalone offline Android unless the Python backend is available locally

## Routing Ownership

Decision:

Routing moves to Python as the primary implementation.

Reason:

The backend can centralize A*, Dijkstra fallback, crowd penalties, nearest checkpoint snapping, route validation, and instruction generation.

## Recognition Ownership

Decision:

Recognition API is Python-owned and currently placeholder/model-ready.

Reason:

Future OpenCV/TensorFlow/TFLite work is easier to develop and test in Python first.
=======
=======
>>>>>>> theirs
# CampusVista Decision Log (Final)

## Major Locked Decisions
1. MVP is strictly outdoor-only.
2. App is true offline-first at runtime.
3. A* is primary router; Dijkstra is fallback; BFS is not final-routing eligible.
4. Crowd awareness uses static time-window penalties from local DB.
5. Runtime does not depend on Python/FastAPI/network.
6. Search is local SQLite first with Java fuzzy fallback.
7. Recognition is optional, on-device TFLite, and outdoor-only.
8. `reference_image_file` stays in Python tooling only, not production SQLite.
9. Static graph is cached; crowd-adjusted costs are computed per route request.
10. Indoor navigation and related data are postponed to future scope.

## Current Honest Status
- Design and scope are fixed.
- Remaining risks are implementation and data quality.
- Risks are mitigated through validation scripts and tests.
<<<<<<< ours
>>>>>>> theirs
=======
>>>>>>> theirs
