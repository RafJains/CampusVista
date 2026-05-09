# Data Collection Plan

## Private Source Data

The public repository intentionally excludes raw campus data. In private local
checkouts, source data is expected under:

```text
python-tools/data/raw/
```

Key files:

- `outdoor_checkpoints.csv`
- `places.csv`
- `edges.csv`
- `crowd_rules.csv`
- `outdoor_panos.csv`
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
python scripts/validate_search_aliases.py
python scripts/generate_seed_db.py
```

## Backend Copy

After data generation, the tooling publishes generated assets into both
`python-backend/data/` for oracle API runtime and
`android-app/app/src/main/assets/` for Android runtime. These outputs are
private/generated and must stay ignored in the public repository.
