import sqlite3
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import campusvista_data as cv  # noqa: E402


class CampusVistaDataPipelineTests(unittest.TestCase):
    def test_final_spec_source_data_validates(self) -> None:
        data, config = cv.validate_all()

        self.assertGreaterEqual(len(data["checkpoints"]), 10)
        self.assertGreaterEqual(len(data["edges"]), 10)
        self.assertEqual(config["meters_per_pixel"], 0.2)

    def test_astar_heuristic_is_admissible_for_all_edges(self) -> None:
        data, config = cv.validate_all()
        checkpoints = {row["checkpoint_id"]: row for row in data["checkpoints"]}
        meters_per_pixel = float(config["meters_per_pixel"])

        for edge in data["edges"]:
            pixel_distance = cv.checkpoint_pixel_distance(
                checkpoints[edge["from_checkpoint_id"]],
                checkpoints[edge["to_checkpoint_id"]],
            )
            heuristic_distance = pixel_distance * meters_per_pixel
            self.assertLessEqual(
                heuristic_distance,
                float(edge["distance_meters"]) + 1e-9,
                edge["edge_id"],
            )

    def test_bidirectional_edges_expand_into_static_graph(self) -> None:
        data, _ = cv.validate_all()
        graph = cv.build_graph(data)

        self.assertIn("OUT_CP002", [edge["to_checkpoint_id"] for edge in graph["OUT_CP001"]])
        self.assertIn("OUT_CP001", [edge["to_checkpoint_id"] for edge in graph["OUT_CP002"]])

    def test_recognition_labels_match_model_indexes(self) -> None:
        data, _ = cv.validate_all()
        labels = cv.load_labels()

        for row in data["recognition_refs"]:
            self.assertEqual(labels[int(row["model_label_index"])], row["label_name"])

    def test_seed_db_uses_locked_mvp_tables(self) -> None:
        data, _ = cv.validate_all()
        expected_tables = {
            "checkpoints",
            "places",
            "edges",
            "crowd_rules",
            "outdoor_panos",
            "recognition_refs",
            "search_aliases",
        }

        db_path = ROOT / "data" / "seed" / "test_campus_seed.db"
        try:
            cv.create_seed_db(data, db_path)

            connection = sqlite3.connect(db_path)
            try:
                tables = {
                    row[0]
                    for row in connection.execute(
                        "SELECT name FROM sqlite_master WHERE type = 'table'"
                    )
                }
                self.assertEqual(expected_tables, tables)
                recognition_columns = {
                    row[1]
                    for row in connection.execute("PRAGMA table_info(recognition_refs)")
                }
                self.assertNotIn("reference_image_file", recognition_columns)
                self.assertEqual(
                    len(data["checkpoints"]),
                    connection.execute("SELECT COUNT(*) FROM checkpoints").fetchone()[0],
                )
            finally:
                connection.close()
        finally:
            if db_path.exists():
                db_path.unlink()


if __name__ == "__main__":
    unittest.main()
