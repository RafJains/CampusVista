# CampusVista Python Backend

This backend is the development oracle for CampusVista. The installed Android
app runs offline with packaged data, while this service is useful for validating
search, routing, pano metadata, and recognition behavior during development.

The public repository does not include generated campus data. Create or restore
private local `python-backend/data/` assets before running the API endpoints.

## Run

```bash
cd python-backend
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

## API

- `GET /health`
- `GET /checkpoints`
- `GET /checkpoints/nearest?x=100&y=900`
- `GET /map/config`
- `GET /places/search?q=library`
- `GET /places/{place_id}`
- `GET /panos/{checkpoint_id}`
- `GET /recognition/refs`
- `GET /recognition/coverage`
- `POST /route`
- `POST /recognize` with multipart field `image`

Example route request:

```json
{
  "start_checkpoint_id": "OUT_CP001",
  "destination_query": "library",
  "route_mode": "shortest"
}
```

Crowd rules are used for warning messages only. The old avoid-crowded route
mode is not part of the final MVP routing flow.

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
