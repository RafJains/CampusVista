<<<<<<< ours
<<<<<<< ours
﻿# Routing Logic

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
=======
=======
>>>>>>> theirs
# CampusVista Routing Logic (Final MVP)

## Algorithms
- Primary: **A***
- Fallback: **Dijkstra**
- Not used for final routing: **BFS**

## Graph Model
- Node = checkpoint
- Edge = walkable outdoor path

## Cost Functions
- Shortest route: `cost = distance_meters`
- Avoid-crowded route: `cost = distance_meters + crowdPenalty(to_checkpoint_id)`

Crowd penalty is applied to **incoming edges** based on `to_checkpoint_id`.

## A* Heuristic
`heuristic = pixel_distance × meters_per_pixel`

Where:
`pixel_distance = sqrt((x2-x1)^2 + (y2-y1)^2)`

## Map Scale Admissibility Validation
Before seed DB generation, validate each edge so heuristic never overestimates:
- `pixel_distance > 0`
- `distance_meters > 0`
- `pixel_distance × meters_per_pixel <= distance_meters`

## Static vs Dynamic Runtime Rule
- Build/cache static graph once (`GraphBuilder`).
- Compute dynamic crowd-adjusted costs per request (`CrowdCostCalculator`).
- Do not rebuild static graph for time changes.

## Bidirectional Edge Handling
If `is_bidirectional = 1`, add `A→B` and `B→A`.
If `is_bidirectional = 0`, add `A→B` only.

## InstructionBuilder MVP Rules
- First movement: `Start walking toward [next_checkpoint_name].`
- Arrival: when `next_checkpoint_id == destination_checkpoint_id`, output `You have arrived at [destination_place_name].`
- Turn logic via vector angle thresholds:
  - Straight: `-30° to +30°`
  - Right: `> +30° to +150°`
  - Left: `< -30° to -150°`
  - Turn back: `>= +150° or <= -150°`

## No Route Fallback
If no route exists, show:
`No outdoor route found between these points.`
Then offer start/destination re-selection and return-to-map actions.
<<<<<<< ours
>>>>>>> theirs
=======
>>>>>>> theirs
