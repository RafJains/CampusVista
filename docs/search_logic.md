<<<<<<< ours
<<<<<<< ours
﻿# Search Logic

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
=======
=======
>>>>>>> theirs
# CampusVista Search Logic (Final MVP)

## Offline Search Pipeline
### Level 1: SQLite Case-insensitive Search
Run local SQL on `places` + alias join on `search_aliases` using `LOWER(...) LIKE LOWER(?)` with `%query%`.

### Level 2: Java Fuzzy Fallback
If SQL results are weak/empty:
- Load place names + aliases
- Rank with Levenshtein distance in `SearchMatcher.java`
- Return top matches

## Supported Search Types
- Exact match
- Keyword match
- Alias match
- Case-insensitive match
- Local fuzzy match fallback

## Search Result Requirements
Each result card should include:
- Place name
- Place type
- Linked outdoor checkpoint
- Short description
- Navigate action

## MVP Search Scope
Search targets are outdoor-facing entities (gates, building entrances, canteen/library fronts, landmarks, junctions, parking, major outdoor areas).
<<<<<<< ours
>>>>>>> theirs
=======
>>>>>>> theirs
