# CampusVista Architecture

CampusVista is now an offline-native Android app with Python kept as the
development oracle and data/model generation environment.

```text
Android App
  -> packaged SQLite seed database
  -> packaged JSON/map/pano assets
  -> Android-native search/routing/pano services
  -> local recognition fallback / placeholder flow

Python Backend / Tools
  -> validate behavior
  -> regenerate seed DB/assets/indexes
  -> provide parity fixtures
```

## Android Runtime Responsibilities

Android owns all installed-app behavior:

- splash screen and startup validation
- home/map screen
- current location selection
- place search and aliases
- place and checkpoint lookup
- nearest-checkpoint snapping
- graph routing and instruction generation
- crowd warning calculation
- outdoor navigation screen
- pano metadata lookup and image display
- camera/gallery photo recognition
- safe recognition fallback behavior without ONNX in the default APK

The release app must not require a Python process, local network, USB tunnel, or
backend URL.

## Python Responsibilities

Python remains useful, but not as a production runtime dependency:

- FastAPI parity/oracle endpoints for local development
- SQLite seed DB generation
- raw CSV/asset validation
- optional recognition index generation and benchmark tooling
- backend fixture tests
- route/search/pano/recognition behavior validation

## Data Flow

Raw and processed data are generated under `python-tools/`, copied into
`python-backend/data/` for the oracle backend, and packaged into
`android-app/app/src/main/assets/` for the installed app.

The duplicated copies are intentional:

- `python-tools/data/raw/`: source-of-truth import inputs
- `python-tools/data/processed/`: generated intermediate JSON fixtures
- `python-backend/data/`: backend oracle/runtime fixtures
- `android-app/app/src/main/assets/`: Play Store app payload

Do not delete one of these copies unless the generation pipeline and every
runtime reference are updated at the same time.

## Recognition

Android ONNX Runtime was removed from the default APK to avoid native library
packaging issues, including Android 16 KB page-size warnings. Recognition is
currently a safe placeholder/fallback flow unless optional model and reference
assets are added later. Core demo features such as map display, search, routing,
crowd warnings, and pano navigation do not depend on ONNX.

## Backend

The Python backend can still be started for development:

```bash
cd python-backend
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

It should be treated as a test oracle and fixture server, not as a production
dependency for the Android release build.
