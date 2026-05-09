# Search Logic

Search lives in two maintained implementations:

- `python-backend/app/services/search_service.py` for the development oracle
- Android local repositories for the installed offline app

The backend searches:

- place names
- keywords
- search aliases

## Matching

Search uses normalized lowercase text and scores candidates by:

- exact match
- prefix match
- substring match
- token overlap
- fuzzy similarity via Python standard-library `difflib.SequenceMatcher`

Results are grouped by place and sorted by best score.

## Endpoint

```text
GET /places/search?q=library
```

Optional filters:

```text
GET /places/search?q=block&place_type=academic_block&limit=5
```

## Android Usage

The installed Android app searches the packaged SQLite seed database directly.
Backend search remains useful for parity tests and local development checks.
