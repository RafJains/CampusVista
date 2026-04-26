# Decision Log

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
