# Search Logic

Search now lives primarily in `python-backend/app/services/search_service.py`.

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

Android should send raw user input to the backend and display the returned `SearchResultOut` list. Android should not duplicate fuzzy search logic once Retrofit integration is complete.
