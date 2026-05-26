# CampusVista: Project Plan and Progress

## 1. What We Planned

### 1.1 Project Idea

CampusVista was planned as a smart campus navigation app for helping users move
around campus using a 2D map, searchable destinations, checkpoints, route
generation, and outdoor pano images.

### 1.2 Planned Architecture

The project started with Android as the mobile interface and Python as the
backend/data-processing environment. As the project matured, the final demo
architecture became offline-native Android, with Python kept for data
generation, validation, and backend parity checks.

### 1.3 Planned Features

- Campus map with checkpoint markers.
- Search for gates, buildings, canteen, library, facilities, and outdoor places.
- Current-location selection from checkpoints or map taps.
- Shortest-path route generation using graph-based routing.
- Crowd warning cards or popups without changing route cost.
- Outdoor pano images linked to checkpoints.
- Street View-style pano switching during navigation.
- Map zoom/pan and pano drag/zoom interactions.
- Recognition/camera flow kept as placeholder/fallback unless optional model
  assets are added.

### 1.4 Planned Data Flow

The intended data flow uses `campus_data.xlsx`, `campus_map.pdf`, and
`outdoor_panos.zip` as private source inputs. Python tooling extracts the map,
detects checkpoints, fills coordinates, calculates edge distances, normalizes
pano assets, generates search aliases, and builds a SQLite seed database. The
generated database and assets are then copied into Android assets and backend
data folders.

### 1.5 Planned Future Scope

Future work can include more pano coverage, full recognition model assets,
indoor navigation, stronger UI polish, an admin upload tool, and live crowd data
if required.

## 2. Progress From Start Till Now

### 2.1 Initial Setup

- Created the CampusVista project structure.
- Set up the Android Studio Java/XML app.
- Added Python backend and tooling folders.
- Established Git/GitHub-friendly repository organization.

### 2.2 Data Pipeline Progress

- Added Python scripts for importing real campus data.
- Built seed database generation and validation flow.
- Added support for Excel, map PDF extraction, pano ZIP normalization, search
  alias generation, and SQLite output.
- Updated the map scale to `meters_per_pixel = 0.1489`.

### 2.3 Android App Progress

- Built the Android app shell and main activities.
- Added map display, search, place details, route preview, route options,
  outdoor navigation, pano viewing, and camera/recognition placeholder flow.
- Added local SQLite asset loading and local fallback logic.
- Added map zoom/pan, tap-to-nearest-checkpoint, pano swipe/drag, and pano
  zoom behavior.

### 2.4 Python Backend Progress

- Built FastAPI endpoints for health checks, checkpoints, map config, place
  search, route generation, pano metadata, and recognition placeholders.
- Implemented backend services for search, routing, crowd warnings, instructions,
  pano lookup, and data access.
- Kept the backend useful for parity checks and development testing.

### 2.5 Real Data Integration

- Integrated real campus data inputs through the Python pipeline.
- Generated checkpoint, edge, place, crowd-rule, alias, map, pano, and seed DB
  outputs.
- Supported missing pano images with placeholder/fallback behavior.

### 2.6 UI and Navigation Improvements

- Modernized the Android UI while keeping the app simple for demo.
- Removed the old Avoid Crowded Path option and kept routing as shortest-path
  only.
- Added crowd warning messages without changing the selected route.
- Added checkpoint-based pano switching during navigation.

### 2.7 Cleanup and Final Sync

- Reduced outdated documentation.
- Removed ONNX Runtime from the default Android APK to avoid native library
  compatibility warnings.
- Kept recognition as a safe placeholder/fallback path so core demo features do
  not depend on ONNX.

### 2.8 Current Working Status

CampusVista currently supports offline Android demo behavior with packaged
SQLite/assets, map navigation, search, shortest-path routing, crowd warnings,
pano navigation, and fallback recognition UI.

### 2.9 Pending Work / Limitations

- Not every checkpoint may have a pano image.
- Recognition remains placeholder/fallback unless optional model/assets are
  added.
- Indoor navigation, live crowd feeds, and admin upload tools are future scope.

Overall, CampusVista has progressed from a basic project idea into a working
campus navigation system with real map data, checkpoint-based routing, search,
pano-based navigation, backend-supported validation, and a cleaned
submission-ready structure.
