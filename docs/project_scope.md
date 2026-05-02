<<<<<<< ours
<<<<<<< ours
﻿# CampusVista Project Scope

## Current MVP

CampusVista provides outdoor campus navigation with an Android frontend and a Python backend.

Included:

- outdoor checkpoints
- outdoor route edges
- 2D map coordinates
- place search
- fuzzy aliases
- A* route calculation
- Dijkstra fallback
- avoid-crowded route mode
- generated outdoor instructions
- outdoor pano metadata
- recognition-ready placeholder API

## Excluded For MVP

- indoor navigation
- room-level routing
- floor maps
- GPS turn-by-turn navigation
- real-time crowd feeds
- production ML recognition model
- admin dashboard

## Transitional Constraint

The Android app still contains the previous offline Java routing code as fallback/reference. The new target architecture makes Python FastAPI the primary brain. The next milestone is to connect Android screens to backend APIs.
=======
=======
>>>>>>> theirs
# CampusVista Project Scope (Final MVP)

## Identity
CampusVista is a true **offline-first Android outdoor campus navigation** app.

## One-line Summary
CampusVista MVP is an offline Android app for outdoor campus navigation using a 2D campus map, outdoor checkpoints, outdoor 360 panoramas, local SQLite storage, A* routing, Dijkstra fallback, static crowd penalties, local search, generated route instructions, and on-device TFLite-based outdoor location recognition.

## Included in the Current MVP
- Outdoor 2D campus map
- Outdoor checkpoint mapping
- Outdoor route edges
- Outdoor shortest-path navigation
- Outdoor avoid-crowded-path navigation
- Outdoor 360 panorama images
- Outdoor camera-based location recognition
- Local SQLite database
- Android-side A* routing
- Android-side Dijkstra fallback
- Static predefined crowd schedules
- Local SQLite search + Java fuzzy fallback
- Generated outdoor text instructions
- Python tooling for preprocessing, validation, seed DB generation, pano preparation, and model training/export

## Not Included in the Current MVP
- Indoor navigation and room-level routing
- Floor maps and floor-to-floor routing
- Indoor images, indoor panoramas, and indoor recognition
- Stairs/lift routing
- GPS-based navigation
- Runtime Python/FastAPI backend dependency
- Real-time crowd tracking
- Personalization
- QR checkpoint dependency
- Full 3D campus model

## Final Locked Statement
CampusVista MVP will be built as a true offline-first Android outdoor campus navigation app in Java. It uses a 2D campus map, outdoor checkpoints, outdoor route edges, outdoor 360 panorama images, local SQLite storage, Android-side A* routing with pixel-to-meter heuristic scaling, Dijkstra fallback, predefined static crowd penalties, local SQLite/Java search, generated outdoor instructions with basic direction logic, and on-device TensorFlow Lite recognition. Indoor scope is intentionally moved to future phases.
<<<<<<< ours
>>>>>>> theirs
=======
>>>>>>> theirs
