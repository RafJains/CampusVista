# Data Collection Plan

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
