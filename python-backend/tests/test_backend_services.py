from __future__ import annotations

import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.services.pano_service import PanoService  # noqa: E402
from app.services.recognition_service import RecognitionService  # noqa: E402
from app.services.routing_service import RoutingService  # noqa: E402
from app.services.search_service import SearchService  # noqa: E402


DB_PATH = ROOT / "data" / "campus_seed.db"


class BackendServiceTests(unittest.TestCase):
    def test_search_finds_place_and_alias(self) -> None:
        service = SearchService(DB_PATH)

        self.assertEqual("PL_LIBRARY", service.search("library", limit=1)[0]["place_id"])
        self.assertEqual("PL_LIBRARY", service.search("libary", limit=1)[0]["place_id"])

    def test_route_uses_astar_and_generates_arrival(self) -> None:
        service = RoutingService(DB_PATH)
        route = service.compute_route(
            {
                "start_checkpoint_id": "OUT_CP001",
                "destination_place_id": "PL_LIBRARY",
                "route_mode": "shortest",
            }
        )

        self.assertTrue(route["route_found"])
        self.assertEqual("astar", route["algorithm"])
        self.assertEqual("OUT_CP001", route["checkpoint_ids"][0])
        self.assertEqual("OUT_CP004", route["checkpoint_ids"][-1])
        self.assertTrue(route["instructions"][0].startswith("Start walking"))
        self.assertEqual("You have arrived at Library.", route["instructions"][-1])

    def test_avoid_crowded_route_applies_penalty(self) -> None:
        service = RoutingService(DB_PATH)
        shortest = service.compute_route(
            {
                "start_checkpoint_id": "OUT_CP001",
                "destination_place_id": "PL_LIBRARY",
                "route_mode": "shortest",
                "now_iso": "2026-04-27T09:30:00",
            }
        )
        avoid = service.compute_route(
            {
                "start_checkpoint_id": "OUT_CP001",
                "destination_place_id": "PL_LIBRARY",
                "route_mode": "avoid_crowded",
                "now_iso": "2026-04-27T09:30:00",
            }
        )

        self.assertTrue(avoid["route_found"])
        self.assertGreaterEqual(avoid["total_cost"], shortest["total_distance"])

    def test_nearest_checkpoint_and_pano_metadata(self) -> None:
        routing = RoutingService(DB_PATH)
        nearest = routing.nearest_checkpoint(98, 904)
        self.assertEqual("OUT_CP001", nearest["checkpoint"]["checkpoint_id"])

        pano = PanoService(DB_PATH).get_pano_for_checkpoint("OUT_CP004")
        self.assertIsNotNone(pano)
        self.assertEqual("OUT_CP004.jpg", pano["image_file"])

    def test_recognition_placeholder_maps_supplied_label(self) -> None:
        service = RecognitionService(DB_PATH)
        response = service.recognize(
            {
                "model_label_index": 1,
                "confidence": 0.82,
            }
        )

        self.assertTrue(response["available"])
        self.assertEqual("accepted", response["status"])
        self.assertEqual("OUT_CP004", response["checkpoint_id"])


if __name__ == "__main__":
    unittest.main()
