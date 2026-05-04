# CampusVista Professor Explanation

## 1. Project Overview

CampusVista is a campus navigation system for helping a user find outdoor routes inside a college campus. The user can open the Android app, select or detect their current checkpoint, search for a destination, preview the route, and start outdoor navigation.

The project is designed around checkpoint-based navigation. A checkpoint is an important outdoor point on the campus map, such as a gate, building entrance, junction, parking area, or landmark. Routes are calculated between these checkpoints using graph-based pathfinding.

In the current MVP, CampusVista supports:

- Campus map display.
- Search for places and buildings.
- Current location selection.
- Shortest-path route generation.
- Step-by-step route instructions.
- Crowd warning messages.
- Outdoor pano viewing for checkpoints that have pano images.
- Offline fallback logic inside Android if the Python backend is unavailable.

The main idea is:

```text
Android app = user interface
Python FastAPI backend = main intelligence layer
SQLite seed database = campus data storage
Android local Java/SQLite = fallback for demo safety
```

## 2. Final Architecture

CampusVista currently uses a Python-heavy architecture.

```text
Android App
   |
   | Retrofit API calls
   v
Python FastAPI Backend
   |
   | SQLite queries and Python services
   v
campus_seed.db + map/pano assets
```

### Android Layer

The Android app is mainly responsible for display and interaction:

- Shows the splash screen, home screen, map, search, route preview, navigation, pano, and camera screens.
- Sends API requests to the Python backend.
- Displays search results, route results, warnings, instructions, and pano images.
- Keeps local fallback repositories and routing classes so the demo can still work if the backend is not running.

### Python Backend Layer

The Python FastAPI backend is the primary intelligence layer:

- Reads the SQLite campus database.
- Performs search and fuzzy matching.
- Builds the graph from checkpoints and edges.
- Calculates the shortest route using A*.
- Falls back to Dijkstra if needed.
- Generates route instructions.
- Looks up pano metadata.
- Produces crowd warning messages.

### SQLite Data Layer

The generated SQLite database stores the campus data:

- Checkpoints.
- Edges.
- Places.
- Place-to-checkpoint mappings.
- Outdoor pano metadata.
- Crowd rules.
- Search aliases.

### Android Fallback Layer

Android still contains local Java/SQLite logic. This is intentionally kept as a fallback. If the Python backend is not running during a demo, the app can still show saved/offline results instead of becoming unusable.

## 3. Why We Use Python

Python is used because most of the "thinking" work is easier, clearer, and more flexible in Python.

Python handles:

- Routing logic: A* shortest path, Dijkstra fallback, graph processing, route validation.
- Search logic: place search, keyword search, alias search, and fuzzy matching.
- Crowd warning logic: reads active crowd rules and returns warning messages.
- Seed DB generation: converts source campus data into a clean SQLite database.
- Checkpoint processing: fills coordinates, calculates distances, and maps real data to checkpoint IDs.

Python is also useful because the data pipeline uses Excel, CSV, PDF image extraction, ZIP processing, and SQLite generation. These tasks are much faster to build and maintain in Python than inside Android Java.

## 4. Why We Use Android

Android is used because the final product is a mobile navigation app.

Android handles:

- User interface and app flow.
- Campus map display.
- Search input and place details.
- Route preview and navigation display.
- Outdoor pano image display.
- Map tap, zoom, and pan interaction.
- Pano swipe/drag and pinch zoom interaction.
- Calling the Python backend through Retrofit.

In simple words, Android is the screen and user experience. Python is the brain.

## 5. Major Project Folders

### `android-app/`

This is the Android Java/XML application. It contains:

- Gradle project files.
- AndroidManifest.
- Java activities and classes.
- XML layouts and drawable resources.
- Android assets such as `campus_seed.db`, `map_config.json`, campus map image, and pano images.
- Retrofit API client code.
- Local fallback repositories and routing classes.

### `python-backend/`

This is the FastAPI backend. It contains:

- API app startup code.
- Route definitions.
- SQLite database access helpers.
- Generated backend data such as `campus_seed.db`, `map_config.json`, campus map image, and pano images.
- Backend tests.

### `python-tools/`

This is the data pipeline and seed database generation area. It contains:

- Source or processed campus data files.
- Data import and validation scripts.
- Seed DB generator.
- Map and pano asset processing.
- Tests for the data pipeline.

### `docs/`

This folder contains project documentation:

- Architecture notes.
- Database schema.
- Data collection plan.
- Routing and search logic.
- Project scope and decision logs.
- This professor explanation document.

## 6. Important Android Files and Classes

### `CampusVistaApp`

`CampusVistaApp` is the custom Android application class. It prepares app-level startup behavior. It is also a natural place for app-wide initialization such as database setup or configuration loading.

### `SplashActivity`

`SplashActivity` is the first screen shown when the app starts. It gives a polished entry point and then moves the user into the main app flow.

### `HomeMapActivity`

`HomeMapActivity` is the main map screen. It displays the campus map, current location status, search and location buttons, checkpoint information, and key place shortcuts.

Important behavior:

- Loads the campus map from assets.
- Supports map zoom and pan.
- Keeps the search bar and controls fixed while only the map area moves.
- Converts tapped screen coordinates back into raw map coordinates.
- Calls the backend nearest-checkpoint API.
- Uses local nearest-checkpoint fallback if the backend is unavailable.

### `SetLocationActivity`

`SetLocationActivity` lets the user set the current location manually. It can show checkpoints and allow the user to select where they are before calculating a route.

### `SearchActivity`

`SearchActivity` lets the user search for a destination. The primary search comes from the Python backend. If the backend is not available, Android can show offline fallback search results.

### `PlaceDetailsActivity`

`PlaceDetailsActivity` shows information about a selected place, such as its name, type, and description. It gives the user a way to start navigation to that place.

### `RoutePreviewActivity`

`RoutePreviewActivity` displays the selected start, end, and distance, then lets the user choose Navigation Steps or Pano Mode.

### `OutdoorNavActivity`

`OutdoorNavActivity` is the active outdoor navigation screen. It shows the current step, route progress, Previous and Next controls, and can switch between map-style navigation and pano-style navigation.

Important behavior:

- Moves checkpoint by checkpoint through the route.
- Displays the instruction for the current step.
- Shows pano metadata if available.
- Shows fallback UI if pano metadata or images are missing.
- Shows crowd warnings when the route passes through currently crowded checkpoints.

### `OutdoorPanoActivity`

`OutdoorPanoActivity` displays a pano image for a checkpoint. It is used when the user wants to view a Street View style checkpoint image.

Important behavior:

- Loads pano metadata and image assets.
- Shows the checkpoint name and pano description.
- Falls back to a placeholder when the pano image is missing.
- Uses `OutdoorPanoViewer` for swipe/drag and pinch zoom behavior.



Current MVP behavior:

- Handles confidence and fallback options.

### `BackendClient`

`BackendClient` is the Retrofit client. It sends HTTP requests from Android to the Python FastAPI backend.

It is responsible for API calls such as:

- `GET /health`
- `GET /places/search`
- `GET /checkpoints`
- `GET /checkpoints/nearest`
- `GET /panos/{checkpoint_id}`
- `POST /route`

It also handles backend failures so Android can switch to fallback behavior.

### `BackendDtos`


### `BackendMapper`

`BackendMapper` converts backend DTO objects into Android local model objects, such as `Checkpoint`, `Place`, and route-related data.

### `DBHelper`, `SeedDbCopier`, and `DBConfig`

These classes manage the Android local SQLite fallback database.

- `DBConfig` stores database constants.
- `SeedDbCopier` copies the generated seed database from Android assets into the app's local storage.
- `DBHelper` opens and manages SQLite access on Android.

### Local Repositories

Android includes local repository classes such as:

- `CheckpointRepository`
- `PlaceRepository`
- `GraphRepository`
- `PanoRepository`
- `CrowdRepository`
- `MapConfigRepository`

These read local data from the Android seed database and assets. They are mainly used as fallback if the backend is unavailable.

### Fallback Routing Classes

Android still contains routing classes such as:

- `Graph`
- `GraphBuilder`
- `RoutePlanner`
- `ShortestPathSearch`
- `NearestCheckpointFinder`
- `InstructionBuilder`
- `RouteResult`

These were originally the main Android-side routing engine. In the final architecture, Python is primary, but these classes remain useful as reference and fallback for demo safety.

## 7. Important Python Backend Files

### `python-backend/app/main.py`

This file creates the FastAPI app.

It does the following:

- Creates the `FastAPI` application.
- Enables CORS so Android can call the backend.
- Includes the API router.
- Serves backend data assets under `/assets`.
- Provides the `/health` endpoint.

The `/health` endpoint is important for demo readiness because it confirms whether the backend and database are available.

### `python-backend/app/routes/api.py`

This file defines the backend API endpoints.

Important endpoints:

- `GET /checkpoints`: returns all checkpoints.
- `GET /checkpoints/nearest`: snaps map coordinates to the nearest checkpoint.
- `GET /checkpoints/{checkpoint_id}`: returns one checkpoint.
- `GET /map/config`: returns map configuration.
- `GET /places/search`: searches places.
- `GET /places/{place_id}`: returns one place.
- `GET /panos/{checkpoint_id}`: returns pano metadata.
- `POST /route`: calculates a route.

### `python-backend/app/db.py`

This file contains SQLite connection helpers.

It:

- Defines the backend data folder.
- Points to `python-backend/data/campus_seed.db`.
- Opens SQLite connections.
- Provides helper functions for fetching one row or multiple rows.
- Allows the database path to be overridden using `CAMPUSVISTA_DB_PATH`.

### `python-backend/app/models.py`

This file defines Pydantic request and response models.

Examples:

- `CheckpointOut`
- `PlaceOut`
- `SearchResultOut`
- `PanoOut`
- `MapConfigOut`
- `RouteRequest`
- `RouteResponse`

These models make the API structured and easy for Android to consume.

### `python-backend/app/services/routing_service.py`

This is the main routing service.

It:

- Reads checkpoints and edges from SQLite.
- Builds the graph.
- Resolves start and destination checkpoints.
- Snaps coordinates to nearest checkpoint if needed.
- Uses A* for shortest-path route generation.
- Uses Dijkstra as a fallback if A* does not return a route.
- Calculates total distance and estimated time.
- Adds instructions, pano metadata, and warnings to the route response.

### `python-backend/app/services/search_service.py`

This service handles place search.

It:

- Reads places and search aliases.
- Searches by place name, keywords, and aliases.
- Uses simple fuzzy matching through text similarity.
- Returns ranked search results with match score and match source.

### `python-backend/app/services/crowd_service.py`

This service handles crowd rules.

Important point: crowd rules do not change routing cost anymore. The app always calculates shortest path. Crowd rules are only used to generate warning messages such as:

```text
This area may be crowded between 12:00 and 14:00.
```

### `python-backend/app/services/instruction_service.py`

This service generates human-readable navigation instructions.

It compares previous, current, and next checkpoint coordinates to generate instructions such as:

- Start walking toward a checkpoint.
- Go straight.
- Turn left.
- Turn right.
- You have arrived at the destination.

### `python-backend/app/services/pano_service.py`

This service looks up pano metadata for a checkpoint.

It returns:

- Pano ID.
- Checkpoint ID.
- Image filename.
- Optional thumbnail filename.
- Image URL under `/assets/pano/outdoor/`.

If a checkpoint has no pano, the Android app shows a placeholder.



It can:

- Accept a label or confidence payload.
- Match the label to a checkpoint if possible.

### `python-backend/app/utils/graph_utils.py`

This file defines:

- `DirectedEdge`
- `Graph`
- `build_graph`

It creates directed graph edges from the database. If an edge is marked bidirectional, it automatically creates the reverse direction as well.

### `python-backend/app/utils/distance_utils.py`

This file contains utility functions for:

- Pixel distance.
- Coordinate distance.
- Text normalization.

### `python-backend/tests/`

This folder contains backend unit tests. The tests verify important backend behavior such as search, routing, pano lookup, crowd warnings, and API/service correctness.

## 8. Important Python Tools Files

### `python-tools/scripts/generate_seed_db.py`

This is the main command-line entry point for generating CampusVista data.

It can:

- Import real data from Excel, map PDF, and pano ZIP.
- Generate processed data files.
- Generate the SQLite seed database.
- Copy generated data into Android assets and backend data folders.

Example:

```powershell
cd E:\Coding\CampusVista
python python-tools\scripts\generate_seed_db.py --workbook C:\Users\rohan\Downloads\campus_data.xlsx --map-pdf C:\Users\rohan\Downloads\campus_map.pdf --panos-zip C:\Users\rohan\Downloads\outdoor_panos.zip
```

### `python-tools/scripts/campusvista_data.py`

This is the main data pipeline module.

It handles:

- Loading campus data.
- Importing Excel sheets.
- Extracting the campus map from PDF.
- Detecting checkpoint marker positions.
- Filling checkpoint coordinates.
- Calculating edge distances.
- Normalizing pano filenames.
- Generating search aliases.
- Validating the data.
- Creating SQLite tables.
- Inserting all data into `campus_seed.db`.
- Copying assets to Android and backend folders.

### Validation Scripts

The `python-tools/scripts/` folder also includes focused validation scripts:

- `validate_checkpoints.py`
- `validate_edges.py`
- `validate_panos.py`
- `validate_crowd_rules.py`
- `validate_search_aliases.py`
- `validate_map_scale.py`
- `build_graph.py`
- `clean_raw_data.py`

These scripts help catch data mistakes before the app is demonstrated.

### `python-tools/tests/`

This folder contains tests for the data pipeline. These tests check that data can be processed, validated, and converted into the expected generated outputs.

## 9. Real Data Pipeline

The real data pipeline is designed so the team does not need to manually create many small files.

The current source inputs are:

- `campus_data.xlsx`
- `campus_map.pdf`
- `outdoor_panos.zip`

### Step 1: Excel Is the Source of Truth

`campus_data.xlsx` is the main structured data file. It contains sheets such as:

- `config`
- `checkpoints`
- `edges`
- `places`
- `panos`
- `crowd_rules`
- `search_aliases`

Minimum required sheets are:

- `config`
- `checkpoints`
- `edges`
- `places`
- `panos`

Optional sheets can be empty.

### Step 2: Campus Map Is Extracted from PDF

`campus_map.pdf` contains the actual campus map image. The pipeline extracts this map and saves it as:

```text
campus_map.png
```

The map has green numbered checkpoint markers from `01` to `75`.

The mapping is:

```text
01 = OUT_CP001
02 = OUT_CP002
...
75 = OUT_CP075
```

### Step 3: Checkpoint Coordinates Are Generated

The team does not manually provide every `x_coord` and `y_coord`.

The pipeline detects the checkpoint marker positions from the marked campus map. It stores both:

- `raw_map_x` and `raw_map_y`: real pixel positions on the map image, useful for rendering and tapping.
- `x_coord` and `y_coord`: normalized coordinates relative to `OUT_CP001`.

`OUT_CP001` is treated as the normalized origin:

```text
OUT_CP001 = (0, 0)
```

This means every other checkpoint position is measured relative to checkpoint 1.

### Step 4: Edge Distances Are Calculated

If the Excel file does not provide `distance_meters`, the pipeline calculates it using:

```text
distance_meters = pixel_distance * meters_per_pixel
```

The current map scale is:

```text
meters_per_pixel = 0.1489
```

Important: checkpoint coordinates are not changed when map scale changes. Only distances and route distance calculations depend on `meters_per_pixel`.

### Step 5: Pano Images Are Normalized

`outdoor_panos.zip` contains available outdoor pano images. The pipeline extracts only the images that are present.

If the Excel file references `.jpg` but the ZIP contains `.jpeg`, the pipeline normalizes filenames safely so the database points to the actual available asset.

Not every checkpoint needs a pano image. If a checkpoint has no pano, Android shows a placeholder and navigation still works.

### Step 6: Search Aliases Are Auto-Generated

If the `search_aliases` sheet is empty, the pipeline generates useful aliases automatically from:

- Place names.
- Keywords.
- Lowercase variants.
- Abbreviations.
- Obvious building/block short forms.
- Place types and checkpoint types.

Aliases are saved with stable IDs such as:

```text
ALIAS_PL_001_001
```

### Step 7: Seed DB and Assets Are Generated

The pipeline generates:

- `python-tools/data/seed/campus_seed.db`
- `python-backend/data/campus_seed.db`
- `android-app/app/src/main/assets/campus_seed.db`
- Backend and Android map config files.
- Backend and Android map image assets.
- Backend and Android pano image assets.

This keeps Android and Python using the same campus data.

## 10. Database Tables

### `checkpoints`

Stores every outdoor checkpoint.

Important fields:

- `checkpoint_id`
- `checkpoint_name`
- `checkpoint_type`
- `x_coord`
- `y_coord`
- `raw_map_x`
- `raw_map_y`
- `latitude`
- `longitude`
- `description`
- `orientation`

### `edges`

Stores walkable connections between checkpoints.

Important fields:

- `edge_id`
- `from_checkpoint_id`
- `to_checkpoint_id`
- `distance_meters`
- `is_bidirectional`
- `edge_type`

If `is_bidirectional = 1`, the backend can travel both ways on that edge.

### `places`

Stores searchable campus places such as libraries, buildings, gates, parking areas, and facilities.

Important fields:

- `place_id`
- `place_name`
- `place_type`
- `checkpoint_id`
- `description`
- `keywords`

### `place_checkpoints`

This extra table supports places that have multiple entrances. It maps one place to one or more checkpoint IDs.

This is useful when a building has multiple entrances and the route should choose the closest valid entrance.

### `outdoor_panos`

Stores pano metadata.

Important fields:

- `pano_id`
- `checkpoint_id`
- `image_file`
- `thumbnail_file`
- `orientation`
- `description`

### `crowd_rules`

Stores time-based crowd information.

Important fields:

- `crowd_rule_id`
- `checkpoint_id`
- `day_type`
- `start_time`
- `end_time`
- `crowd_level`
- `penalty_cost`
- `description`

In the current architecture, `penalty_cost` is not used to change the route. It is kept for data completeness and possible future use.



Important fields:

- `checkpoint_id`
- `reference_image_file`
- `confidence_threshold`

### `search_aliases`

Stores extra searchable terms for places.

Important fields:

- `alias_id`
- `place_id`
- `alias_text`
- `alias_type`

## 11. Route Generation Flow

The route generation flow is:

1. User selects their current location.
2. User searches for a destination.
3. Android sends a `POST /route` request to the Python backend.
4. The backend reads checkpoints, edges, places, panos, and crowd rules from SQLite.
5. The backend builds a graph from the `checkpoints` and `edges` tables.
6. The backend resolves the destination:
   - direct checkpoint ID,
   - place ID,
   - search query,
   - or tapped map coordinate.
7. The backend calculates the shortest path using A*.
8. If A* cannot find a route, Dijkstra is available as a fallback.
9. The backend generates route instructions.
10. The backend attaches pano metadata for each checkpoint if available.
11. The backend checks crowd rules and adds warning messages if needed.
12. Android displays the result.

The route response contains:

- `route_found`
- `algorithm`
- `route_mode`
- `total_distance`
- `estimated_time`
- `checkpoint_ids`
- `checkpoints`
- `edges`
- `instructions`
- `panos`
- `warnings`

Example simplified response:

```json
{
  "route_mode": "shortest",
  "total_distance": 420,
  "estimated_time": "6 min",
  "checkpoint_ids": ["OUT_CP001", "OUT_CP002", "OUT_CP005"],
  "instructions": [
    "Start walking toward Main Path Junction.",
    "Turn left toward Library Front.",
    "You have arrived at Library."
  ],
  "warnings": [
    "Library Front may be medium between 12:00 and 14:00."
  ]
}
```

## 12. Crowd Rules Behavior

Earlier, CampusVista had an "Avoid Crowded Path" option. That feature was removed to keep the MVP simple and predictable.

Current behavior:

- Routing is always shortest path.
- Crowd rules do not add route cost.
- Crowd rules do not change the selected path.
- Crowd rules only show warning messages.

Warnings are shown when:

- The route passes through a checkpoint with an active crowd rule.
- The user is currently at or navigating into a checkpoint with an active crowd rule.

This is easier to explain and safer for the demo because the route behavior remains consistent.

## 13. Pano Navigation

CampusVista supports checkpoint-based pano switching, similar to a simple Street View experience.

How it works:

- Each checkpoint can have one linked pano image in the `outdoor_panos` table.
- The route response includes pano metadata for each checkpoint.
- During navigation, the user can move to the next or previous checkpoint.
- When the current checkpoint changes, the pano view loads the pano for that checkpoint.
- If the checkpoint has no pano image, Android shows a placeholder.

The pano viewer supports:

- Drag/swipe left and right.
- Slight up/down movement.
- Pinch zoom support.
- Double-tap reset where implemented.
- Missing-image fallback.

This gives a lightweight Street View style MVP without adding a heavy 3D engine.

## 14. Map Behavior

The campus map is displayed inside the Android app.

Important behavior:

- The map image comes from generated assets.
- Checkpoints are based on raw map coordinates.
- The user can tap the map to select the nearest checkpoint.
- The app sends tapped map coordinates to the backend nearest-checkpoint API.
- If the backend is unavailable, Android uses local fallback nearest-checkpoint logic.
- The map supports zoom and pan.
- Only the map area zooms and pans. Search bars, buttons, route cards, and other controls remain fixed.

Tap-to-checkpoint accuracy depends on converting screen touch coordinates back into raw map coordinates after zoom and pan.

## 15. Backend Unavailable Fallback

If the Python backend is not running, the Android app may show a friendly fallback message and use local data.

This is intentional.

The reason is demo safety. If the backend server is not started, or the emulator cannot reach the laptop network, the app should still remain usable instead of crashing.


## 16. How to Run the Project

### Start the Python Backend

Open a terminal:

```powershell
cd E:\Coding\CampusVista\python-backend
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Then open:

```text
http://localhost:8000/health
```

Expected result:

```json
{
  "status": "ok",
  "service": "campusvista-python-backend",
  "database": "ready"
}
```

FastAPI documentation is available at:

```text
http://localhost:8000/docs
```

### Run the Android App

1. Open Android Studio.
2. Open the `android-app/` folder.
3. Let Gradle sync.
4. Start an Android emulator.
5. Run the app.

For Android Emulator, the backend base URL should be:

```text
http://10.0.2.2:8000/
```

For a real phone, use the laptop's local IP address:

```text
http://192.168.x.x:8000/
```

The laptop and phone must be on the same network, and the backend must be running with:

```text
--host 0.0.0.0
```

## 17. Demo Flow

A simple demo flow:

1. Start the Python backend.
2. Open `http://localhost:8000/health` and show that the backend is ready.
3. Open `http://localhost:8000/docs` and briefly show the API endpoints.
4. Launch the Android app.
5. Show the splash screen and home map.
6. Set the current location using `SetLocationActivity` or by tapping the map.
7. Search for a place in `SearchActivity`.
8. Open the place details screen.
9. Preview the route.
10. Show distance, estimated time, instructions, and warnings.
11. Start outdoor navigation.
12. Move through route steps with Previous and Next.
13. Open or switch pano view for checkpoints.
14. Show placeholder behavior if a checkpoint has no pano.
15. If an active crowd rule applies, show the crowd warning card or popup.
16. Optionally stop the backend and show that the Android fallback still keeps the app usable.

## 18. Professor/Viva Q&A

### Q1. What is CampusVista?

CampusVista is a campus navigation app. It helps users search for places, select their current checkpoint, calculate a route, view route instructions, and see checkpoint-based outdoor pano images.

### Q2. Why did you use Python?


### Q3. Why did you use FastAPI?

FastAPI is lightweight, fast to develop, and gives automatic API documentation at `/docs`. It is a good fit for connecting the Android frontend to Python services through JSON APIs.

### Q4. Why did you use SQLite?


### Q5. Why A* routing?

A* is efficient for pathfinding when we have coordinates. It uses the actual route cost plus a heuristic estimate to the destination. Here, the heuristic is based on map distance and `meters_per_pixel`, so it can find shortest routes efficiently.

### Q6. Why is Dijkstra also present?

Dijkstra is a reliable fallback pathfinding algorithm. It does not need a heuristic, so it is useful if A* fails or if we want a simpler guaranteed fallback.

### Q7. Why no GPS?

GPS is not accurate enough for detailed campus-level checkpoint navigation, especially near buildings, entrances, and narrow paths. The MVP uses checkpoint-based navigation because it is simpler, more reliable for a campus demo, and works with the marked map.

### Q8. Why was "Avoid Crowded Path" removed?

It was removed to keep routing predictable and demo-friendly. The app now always calculates the shortest path. Crowd data is still useful, but it is shown as a warning instead of changing the route.

### Q9. How are coordinates generated?

The campus map PDF contains green numbered markers. The pipeline extracts the map image, detects these marker positions, maps marker numbers to checkpoint IDs, and stores raw pixel coordinates. `OUT_CP001` is treated as origin `(0,0)`, and other checkpoint coordinates are normalized relative to it.

### Q10. How are route distances generated?

When edge distances are missing, the pipeline calculates pixel distance between connected checkpoints and multiplies it by:

```text
meters_per_pixel = 0.1489
```

This gives approximate walking distance in meters.

### Q11. What happens if the backend fails?

Android shows a user-friendly fallback message and uses local SQLite/repository logic where possible. This is intentional so the demo does not fail completely if the backend is not running.

### Q12. How are pano images linked?

Each pano row links a `checkpoint_id` to an image filename. During route generation, the backend returns pano metadata for each route checkpoint. Android loads the matching image when the user navigates to that checkpoint.

### Q13. What happens if a pano is missing?

The app shows a placeholder image and keeps navigation working. Missing panos do not break the route.



### Q15. What is the future scope?


## 19. Current Limitations

- Only available pano images are used. Not every checkpoint has a pano yet.
- A real phone needs correct network configuration and the laptop IP address.
- The Python backend must be running for the full Python-heavy demo.
- Android fallback is useful, but it is not the main intended architecture.
- Map and pano pinch zoom should be manually tested on the emulator or phone before final demo.
- Checkpoint detection depends on the quality and consistency of the marked campus map PDF.
- Edge distances generated from map pixels are approximate and depend on the correctness of `meters_per_pixel`.

## 20. Future Scope

Planned or possible improvements:

- More pano images for all checkpoints.
- True 360 spherical rendering for panos.
- Indoor navigation inside buildings.
- Better route visualization with animated progress.
- Admin data upload tool for Excel, map, and pano updates.
- Live crowd data if needed later.
- Better map calibration and real walking-distance verification.
- More UI polish and accessibility improvements.

## 21. Short Viva Summary

CampusVista is a checkpoint-based campus navigation app. Android is used as the mobile user interface, while Python FastAPI is used as the intelligence layer. Campus data is stored in a generated SQLite database. The Python backend performs search, route calculation, instruction generation, pano lookup, and crowd warning logic. Android displays the map, route, instructions, pano images, and camera placeholder flow. If the backend is unavailable, Android has local fallback logic so the demo can still continue.

The system is data-driven. The team provides `campus_data.xlsx`, `campus_map.pdf`, and `outdoor_panos.zip`. The Python tools extract the map, detect checkpoints, calculate coordinates and distances using `meters_per_pixel = 0.1489`, normalize pano assets, generate search aliases, and build the seed database. This makes the project easier to update when real campus data changes.
