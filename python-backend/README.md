# CampusVista Python Backend

This backend is now the primary intelligence layer for CampusVista.

Android is the Java/XML frontend. Python owns SQLite access, search, fuzzy search, graph routing, crowd-aware costs, nearest-checkpoint snapping, route validation, instruction generation, panorama metadata, and recognition-ready APIs.

## Run

```bash
cd python-backend
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Android emulator URL:

```text
http://10.0.2.2:8000
```

Real phone URL:

```text
http://<laptop-ip>:8000
```

## API

- `GET /health`
- `GET /checkpoints`
- `GET /checkpoints/nearest?x=100&y=900`
- `GET /map/config`
- `GET /places/search?q=library`
- `GET /places/{place_id}`
- `GET /panos/{checkpoint_id}`
- `POST /route`
- `GET /recognition/refs`
- `POST /recognize`

Example route request:

```json
{
  "start_checkpoint_id": "OUT_CP001",
  "destination_query": "library",
  "route_mode": "avoid_crowded"
}
```

Example response shape:

```json
{
  "route_found": true,
  "algorithm": "astar",
  "route_mode": "shortest",
  "total_distance": 191,
  "estimated_time": "3 min",
  "checkpoint_ids": ["OUT_CP001", "OUT_CP002", "OUT_CP003", "OUT_CP004"],
  "instructions": [
    "Start walking toward Main Path Junction.",
    "Turn left toward Admin Block Entrance.",
    "Turn right toward Library Front.",
    "You have arrived at Library."
  ]
}
```
