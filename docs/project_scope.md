# CampusVista Project Scope

## Current MVP

CampusVista provides offline outdoor campus navigation with an Android runtime
and Python development tooling.

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

## Runtime Constraint

The Android app is the primary runtime. Python FastAPI remains in the project as
a development oracle and validation fixture source.
