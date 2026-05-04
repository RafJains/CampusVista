# CampusVista Project Scope

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

## Excluded For MVP

- indoor navigation
- room-level routing
- floor maps
- GPS turn-by-turn navigation
- real-time crowd feeds
- admin dashboard

## Transitional Constraint

The Android app still contains the previous offline Java routing code as fallback/reference. The new target architecture makes Python FastAPI the primary brain. The next milestone is to connect Android screens to backend APIs.
