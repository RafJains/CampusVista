from __future__ import annotations

import sys
import json
import tempfile
import unittest
from io import BytesIO
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.services.pano_service import PanoService  # noqa: E402
from app.services.recognition_service import RecognitionService  # noqa: E402
from app.services.routing_service import RoutingService  # noqa: E402
from app.services.search_service import SearchService  # noqa: E402
from app.utils.image_uploads import extract_image_upload  # noqa: E402
from campusvista_recognition import (  # noqa: E402
    EMBEDDING_DIMENSION,
    MODEL_VERSION,
    create_encoder,
    encoder_for_model_version,
)


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

    def test_recognition_returns_ordered_matches_for_known_pano(self) -> None:
        service = RecognitionService(DB_PATH)
        image_bytes = (ROOT / "data" / "pano" / "outdoor" / "OUT_CP001.jpg").read_bytes()

        result = service.recognize(image_bytes, "image/jpeg")

        self.assertIn("recognized", result)
        self.assertGreater(len(result["matches"]), 0)
        self.assertEqual(1, result["matches"][0]["rank"])
        self.assertGreaterEqual(result["matches"][0]["confidence_percent"], 0)
        self.assertLessEqual(result["matches"][0]["confidence_percent"], 99)

    def test_recognition_coverage_reports_supported_checkpoints(self) -> None:
        coverage = RecognitionService(DB_PATH).get_coverage_summary()

        self.assertEqual(75, coverage["checkpoint_count"])
        self.assertEqual(59, coverage["supported_checkpoint_count"])
        self.assertGreater(coverage["embedding_count"], coverage["supported_checkpoint_count"])
        self.assertTrue(coverage["model_version"].startswith("campusvista-vpr-openclip-"))

    def test_recognition_rejects_invalid_uploads_and_caches_index(self) -> None:
        service = RecognitionService(DB_PATH)

        with self.assertRaises(ValueError):
            service.recognize(b"not an image", "text/plain")
        with self.assertRaises(ValueError):
            service.recognize(b"", "image/jpeg")
        blank = BytesIO()
        Image.new("RGB", (224, 224), "white").save(blank, format="JPEG")
        self.assertFalse(service.recognize(blank.getvalue(), "image/jpeg")["recognized"])

        first = service._load_index()
        second = service._load_index()
        self.assertIs(first, second)

    def test_recognition_encoder_defaults_to_handcrafted_model(self) -> None:
        encoder = create_encoder("handcrafted")

        self.assertEqual(MODEL_VERSION, encoder.model_version)
        self.assertEqual(EMBEDDING_DIMENSION, encoder.embedding_dimension)
        self.assertIs(create_encoder("handcrafted"), encoder)
        self.assertIs(encoder_for_model_version(MODEL_VERSION).__class__, encoder.__class__)

    def test_recognition_rejects_mismatched_index_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)
            index_path = temp_path / "recognition_index.npz"
            metadata_path = temp_path / "recognition_metadata.json"
            np.savez_compressed(
                index_path,
                embeddings=np.zeros((1, EMBEDDING_DIMENSION), dtype=np.float32),
                checkpoint_ids=np.array(["OUT_CP001"]),
                image_files=np.array(["OUT_CP001.jpg"]),
                model_version=np.array(MODEL_VERSION),
            )
            metadata_path.write_text(
                json.dumps(
                    {
                        "model_version": "wrong-version",
                        "embedding_dimension": EMBEDDING_DIMENSION,
                    }
                ),
                encoding="utf-8",
            )

            service = RecognitionService(DB_PATH, index_path=index_path, metadata_path=metadata_path)

            with self.assertRaises(ValueError):
                service._load_index()

    def test_image_upload_parser_accepts_raw_and_multipart_images(self) -> None:
        raw_bytes, raw_type = extract_image_upload(b"jpeg-bytes", "image/jpeg")
        self.assertEqual(b"jpeg-bytes", raw_bytes)
        self.assertEqual("image/jpeg", raw_type)

        boundary = "campusvista"
        multipart = (
            b"--campusvista\r\n"
            b'Content-Disposition: form-data; name="image"; filename="photo.jpg"\r\n'
            b"Content-Type: image/jpeg\r\n"
            b"\r\n"
            b"multipart-jpeg-bytes\r\n"
            b"--campusvista--\r\n"
        )
        image_bytes, image_type = extract_image_upload(
            multipart,
            f"multipart/form-data; boundary={boundary}",
        )

        self.assertEqual(b"multipart-jpeg-bytes", image_bytes)
        self.assertEqual("image/jpeg", image_type)


if __name__ == "__main__":
    unittest.main()
