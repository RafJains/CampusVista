# Decision Log

## Offline-Native Android Pivot

Decision:

CampusVista release builds run fully offline on Android. Python remains a
development oracle and data/model generator.

```text
Android = UI + local engine + packaged assets + on-device recognition
Python = fixture generation, backend parity, validation, ML export
```

Reason:

The product goal is Play Store distribution where the installed app works
without a laptop backend, Wi-Fi, USB tunnel, or localhost server.

Impact:

- Android runtime backend calls were removed from release paths.
- `CampusVistaEngine` became the Android facade for search, routing, panos, and recognition.
- OpenCLIP-style mobile recognition assets are packaged with ONNX Runtime Mobile.
- The Python backend remains available for parity tests and data/index generation.

## Historical Architecture Pivot

Decision:

CampusVista will use a Python-heavy architecture.

```text
Android = UI/display layer
Python = backend and intelligence layer
```

Reason:

The project now prioritizes Python/backend work. Python is better suited for graph algorithms, fuzzy search, and data validation.

Impact:

- FastAPI backend added under `python-backend/`
- Android Retrofit client was added under `android-app/app/src/main/java/com/example/campusvista/network/`
- Android local routing remained as fallback/reference
- At that time, the system was no longer standalone offline Android unless the Python backend was available locally

Status:

Superseded by the offline-native Android pivot above.

## Routing Ownership

Decision:

Routing moves to Python as the primary implementation.

Reason:

The backend can centralize A*, Dijkstra fallback, crowd penalties, nearest checkpoint snapping, route validation, and instruction generation.
