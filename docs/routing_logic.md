# Routing Logic

Routing now lives primarily in `python-backend/app/services/routing_service.py`.

CampusVista models the campus as a weighted graph:

```text
checkpoint = graph node
walkable path = weighted edge
```

## Shortest Path

```text
edge_cost = distance_meters
```

## Avoid Crowded Path

```text
edge_cost = distance_meters + crowd_penalty(to_checkpoint_id)
```

Crowd penalties are read from `crowd_rules` for the current day type and time. The backend also accepts `now_iso` in route requests for deterministic tests and demos.

## Algorithms

Primary:

- A*
- heuristic = Euclidean pixel distance * meters_per_pixel

Fallback:

- Dijkstra

BFS is not used because the graph is weighted.

## Route Request Inputs

The backend can route from:

- `start_checkpoint_id`
- or `start_x` and `start_y`, snapped to nearest checkpoint

The backend can route to:

- `destination_checkpoint_id`
- `destination_place_id`
- `destination_query`
- or `destination_x` and `destination_y`, snapped to nearest checkpoint

## Route Response

The backend returns:

- route mode
- algorithm used
- total distance
- total route cost
- estimated walking time
- checkpoint IDs
- checkpoint objects
- traversed edges
- generated instructions
- validation warnings
