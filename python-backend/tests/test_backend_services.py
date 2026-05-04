from __future__ import annotations

import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.services.pano_service import PanoService  # noqa: E402
from app.services.routing_service import RoutingService  # noqa: E402
from app.services.search_service import SearchService  # noqa: E402


DB_PATH = ROOT / "data" / "campus_seed.db"


class BackendServiceTests(unittest.TestCase):
    def test_search_finds_place_and_alias(self) -> None:
        service = SearchService(DB_PATH)

        self.assertEqual("PL_002", service.search("cafeteria", limit=1)[0]["place_id"])
        self.assertEqual("PL_002", service.search("canteen", limit=1)[0]["place_id"])

    def test_route_uses_astar_and_generates_arrival(self) -> None:
        service = RoutingService(DB_PATH)
        route = service.compute_route(
            {
                "start_checkpoint_id": "OUT_CP001",
                "destination_place_id": "PL_002",
                "route_mode": "shortest",
            }
        )

        self.assertTrue(route["route_found"])
        self.assertEqual("astar", route["algorithm"])
        self.assertEqual("OUT_CP001", route["checkpoint_ids"][0])
        self.assertEqual("OUT_CP040", route["checkpoint_ids"][-1])
        self.assertTrue(route["instructions"][0].startswith("Start walking"))
        self.assertEqual("You have arrived at Cafeteria.", route["instructions"][-1])
        self.assertEqual(len(route["checkpoints"]), len(route["panos"]))
        self.assertEqual("OUT_CP001", route["panos"][0]["checkpoint_id"])
        self.assertTrue(any(pano and pano["checkpoint_id"] == "OUT_CP004" for pano in route["panos"]))

    def test_crowd_rules_create_warnings_without_cost_penalty(self) -> None:
        service = RoutingService(DB_PATH)
        route = service.compute_route(
            {
                "start_checkpoint_id": "OUT_CP001",
                "destination_place_id": "PL_002",
                "route_mode": "shortest",
                "now_iso": "2026-05-04T08:50:00",
            }
        )

        self.assertTrue(route["route_found"])
        self.assertEqual(route["total_distance"], route["total_cost"])
        self.assertTrue(any("may be" in warning for warning in route["warnings"]))

    def test_nearest_checkpoint_and_pano_metadata(self) -> None:
        routing = RoutingService(DB_PATH)
        nearest = routing.nearest_checkpoint(97, 43)
        self.assertEqual("OUT_CP001", nearest["checkpoint"]["checkpoint_id"])

        pano = PanoService(DB_PATH).get_pano_for_checkpoint("OUT_CP004")
        self.assertIsNotNone(pano)
        self.assertEqual("OUT_CP004.jpg", pano["image_file"])


if __name__ == "__main__":
    unittest.main()
