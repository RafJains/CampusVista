<<<<<<< ours
<<<<<<< ours
﻿# Data Collection Plan

## Current Data

Current MVP data lives in:

```text
python-tools/data/raw/
```

Key files:

- `outdoor_checkpoints.csv`
- `places.csv`
- `edges.csv`
- `crowd_rules.csv`
- `outdoor_panos.csv`
- `recognition_refs.csv`
- `search_aliases.csv`

## Validation

Run the Python tools before regenerating the seed DB:

```bash
cd python-tools
python scripts/validate_checkpoints.py
python scripts/validate_edges.py
python scripts/validate_map_scale.py
python scripts/validate_crowd_rules.py
python scripts/validate_panos.py
python scripts/validate_recognition_labels.py
python scripts/validate_search_aliases.py
python scripts/generate_seed_db.py
```

## Backend Copy

After data generation, copy the seed DB and assets into `python-backend/data/` for API runtime. This keeps Android assets and backend assets explicit during the architecture transition.

Future improvement:

Add a single sync script that publishes generated data to both Android and Python backend asset folders.
=======
=======
>>>>>>> theirs
# CampusVista Data Collection Plan (Outdoor-only MVP)

## Data Entities
- Outdoor checkpoints
- Places linked to outdoor checkpoints
- Outdoor edges
- Crowd rules
- Outdoor pano metadata
- Recognition refs (Python + Android variants)
- Search aliases

## Collection Rules
### Checkpoints
Collect: `checkpoint_id`, `checkpoint_name`, `checkpoint_type`, `x_coord`, `y_coord`, optional geo/orientation/notes.

### Edges
Collect: `edge_id`, `from_checkpoint_id`, `to_checkpoint_id`, `distance_meters`, `is_bidirectional`, `edge_type`.

### Distance Rule
Recommended method: `distance_meters = pixel_distance × meters_per_pixel` after map calibration.
Validation requires:
- `pixel_distance > 0`
- `distance_meters > 0`
- `pixel_distance × meters_per_pixel <= distance_meters`

### Crowd Rules
Collect: `day_type`, `start_time`, `end_time`, `crowd_level`, `penalty_cost`.
Rules:
- `day_type ∈ {weekday, weekend}`
- time format `HH:MM`
- no overlap for same checkpoint/day
- `penalty_cost >= 0`

### Outdoor Pano Assets
- JPEG only
- Recommended: 2048×1024
- Max: 4096×2048
- Max size per pano: 2 MB
- Total pano budget: under 60 MB
- Store panos for key outdoor points only (gates, entrances, landmarks, key junctions)

### Recognition Data Split
- Python raw data includes `reference_image_file` for traceability/training.
- Android production DB excludes `reference_image_file`.

### Filename Rule
DB image fields must store filename only (no `/` or `\`, must end in `.jpg/.jpeg`).
<<<<<<< ours
>>>>>>> theirs
=======
>>>>>>> theirs
