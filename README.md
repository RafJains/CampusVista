# CampusVista

CampusVista is an offline-first Android campus navigation app for outdoor campus routing. It uses a 2D campus map, outdoor checkpoints, 360 panorama images, local SQLite data, A* routing, Dijkstra fallback, static crowd penalties, local search, and optional on-device TensorFlow Lite outdoor location recognition.

## Features

- Offline outdoor campus navigation
- 2D campus map with checkpoint markers
- A* shortest-path routing
- Dijkstra fallback routing
- Avoid-crowded-path option
- Local SQLite database
- Local search with alias and fuzzy fallback
- Outdoor 360 panorama viewer
- Optional TFLite-based outdoor location recognition
- Generated outdoor route instructions

## MVP Scope

### Included

- Outdoor checkpoints
- Outdoor route edges
- Outdoor map navigation
- Outdoor 360 panoramas
- Local search
- Static crowd-aware routing
- Android-side routing
- SQLite seed database
- Python tools for data validation and DB/model generation

### Not Included

- Indoor navigation
- Room-level routing
- GPS navigation
- Real-time crowd tracking
- Backend/FastAPI runtime dependency
- Full 3D campus model
- Personalization
- QR checkpoint dependency

## Tech Stack

### Android

- Android Studio
- Java
- XML layouts
- SQLite
- SQLiteOpenHelper / DBHelper
- TensorFlow Lite
- CameraX or Camera Intent
- Custom map rendering

### Python Tools

- Python
- pandas
- numpy
- sqlite3
- TensorFlow
- OpenCV
- pytest

## Routing Logic

CampusVista models the campus as a graph.

```text
Checkpoint = Node
Walkable path = Edge
````

Shortest path:

```text
cost = distance_meters
```

Avoid crowded path:

```text
cost = distance_meters + crowdPenalty(to_checkpoint_id)
```

A* is used for primary routing. Dijkstra is used as fallback. BFS is not used for final routing because the graph is weighted.

## Database Tables

The MVP uses these SQLite tables:

* `checkpoints`
* `places`
* `edges`
* `crowd_rules`
* `outdoor_panos`
* `recognition_refs`
* `search_aliases`

## Folder Structure

```text
CampusVista/
├── android-app/
│   └── app/src/main/
│       ├── java/com/example/campusvista/
│       │   ├── ui/
│       │   ├── viewmodel/
│       │   ├── data/
│       │   ├── routing/
│       │   ├── recognition/
│       │   ├── map/
│       │   ├── pano/
│       │   ├── search/
│       │   └── util/
│       ├── res/
│       ├── assets/
│       │   ├── config/map_config.json
│       │   ├── seed/campus_seed.db
│       │   ├── maps/campus_map.png
│       │   ├── pano/outdoor/
│       │   └── ml/
│       └── AndroidManifest.xml
│
├── python-tools/
│   ├── data/
│   ├── scripts/
│   ├── tests/
│   └── requirements.txt
│
├── docs/
├── README.md
└── .gitignore
```

## Setup

Clone the repo:

```bash
git clone https://github.com/your-username/CampusVista.git
cd CampusVista
```

Open this folder in Android Studio:

```text
CampusVista/android-app
```

Required Android assets:

```text
assets/config/map_config.json
assets/seed/campus_seed.db
assets/maps/campus_map.png
assets/pano/outdoor/
assets/ml/campus_location_model.tflite
assets/ml/labels.txt
```

The TFLite model is optional until recognition is implemented.

## Python Tools Setup

```bash
cd python-tools
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
```

Run validations:

```bash
python scripts/validate_checkpoints.py
python scripts/validate_edges.py
python scripts/validate_crowd_rules.py
python scripts/validate_map_scale.py
python scripts/generate_seed_db.py
```

## Future Scope

* Indoor navigation
* Room-level search
* Floor maps
* Indoor 360 panoramas
* GPS-assisted positioning
* Real-time crowd estimation
* Admin dashboard
* Backend-based sync
* Advanced recognition

## Status

```text
MVP design finalized
Outdoor-only scope locked
Offline-first architecture finalized
Implementation in progress
```

## Authors

CampusVista Team

```
```
