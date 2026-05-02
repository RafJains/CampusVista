<<<<<<< ours
<<<<<<< ours
﻿# Team Division

## Android Frontend

Owns:

- Java/XML screens
- Retrofit API client
- loading and error UI
- route display
- pano image display
- camera capture

## Python Backend

Owns:

- FastAPI routes
- SQLite access
- search and fuzzy matching
- graph building
- A* and Dijkstra routing
- crowd-aware route costs
- route validation
- instruction generation
- recognition-ready API

## Data/ML

Owns:

- raw CSV data
- map config
- pano and recognition reference assets
- seed DB generation
- future OpenCV/TensorFlow/TFLite model work
=======
=======
>>>>>>> theirs
# CampusVista Team Division (Final MVP)

## Person 1 — Android UI + ViewModel
- Splash/home/search/place details/route preview/outdoor nav/outdoor pano screens
- XML layouts and ViewModels
- Route-unavailable and recognition-fallback UI flows

## Person 2 — SQLite + Data Pipeline
- DB schema and seed DB generation
- `DBHelper`, `SeedDbCopier`, `QueryMapper`, repository layer
- Python validation scripts
- DB copy/version safety and map config generation

## Person 3 — Routing + Graph
- Graph model/building
- A* + Dijkstra
- Heuristic scaling and map-scale checks
- Crowd cost logic
- Nearest checkpoint and instruction generation

## Person 4 — Recognition + Integration
- TFLite model pipeline
- Camera preprocessing + confidence handling
- Label mapping and fallback
- Pano-recognition integration tests
<<<<<<< ours
>>>>>>> theirs
=======
>>>>>>> theirs
