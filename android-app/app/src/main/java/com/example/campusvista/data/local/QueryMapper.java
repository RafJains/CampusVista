package com.example.campusvista.data.local;

import android.database.Cursor;

import com.example.campusvista.data.model.Checkpoint;
import com.example.campusvista.data.model.CrowdRule;
import com.example.campusvista.data.model.Edge;
import com.example.campusvista.data.model.OutdoorPano;
import com.example.campusvista.data.model.Place;
import com.example.campusvista.data.model.RecognitionRef;
import com.example.campusvista.data.model.SearchAlias;

import java.util.ArrayList;
import java.util.List;

public final class QueryMapper {
    private QueryMapper() {
    }

    public static Checkpoint toCheckpoint(Cursor cursor) {
        return new Checkpoint(
                getString(cursor, "checkpoint_id"),
                getString(cursor, "checkpoint_name"),
                getString(cursor, "checkpoint_type"),
                getDouble(cursor, "x_coord"),
                getDouble(cursor, "y_coord"),
                getNullableDouble(cursor, "latitude"),
                getNullableDouble(cursor, "longitude"),
                getNullableString(cursor, "description"),
                getNullableString(cursor, "orientation")
        );
    }

    public static Place toPlace(Cursor cursor) {
        return new Place(
                getString(cursor, "place_id"),
                getString(cursor, "place_name"),
                getString(cursor, "place_type"),
                getString(cursor, "checkpoint_id"),
                getNullableString(cursor, "description"),
                getNullableString(cursor, "keywords")
        );
    }

    public static Edge toEdge(Cursor cursor) {
        return new Edge(
                getString(cursor, "edge_id"),
                getString(cursor, "from_checkpoint_id"),
                getString(cursor, "to_checkpoint_id"),
                getDouble(cursor, "distance_meters"),
                getInt(cursor, "is_bidirectional") == 1,
                getNullableString(cursor, "edge_type")
        );
    }

    public static CrowdRule toCrowdRule(Cursor cursor) {
        return new CrowdRule(
                getString(cursor, "crowd_rule_id"),
                getString(cursor, "checkpoint_id"),
                getString(cursor, "day_type"),
                getString(cursor, "start_time"),
                getString(cursor, "end_time"),
                getString(cursor, "crowd_level"),
                getDouble(cursor, "penalty_cost"),
                getNullableString(cursor, "description")
        );
    }

    public static OutdoorPano toOutdoorPano(Cursor cursor) {
        return new OutdoorPano(
                getString(cursor, "pano_id"),
                getString(cursor, "checkpoint_id"),
                getString(cursor, "image_file"),
                getNullableString(cursor, "thumbnail_file"),
                getNullableString(cursor, "orientation"),
                getNullableString(cursor, "description")
        );
    }

    public static RecognitionRef toRecognitionRef(Cursor cursor) {
        return new RecognitionRef(
                getString(cursor, "recognition_id"),
                getString(cursor, "checkpoint_id"),
                getString(cursor, "label_name"),
                getInt(cursor, "model_label_index"),
                getDouble(cursor, "confidence_threshold")
        );
    }

    public static SearchAlias toSearchAlias(Cursor cursor) {
        return new SearchAlias(
                getString(cursor, "alias_id"),
                getString(cursor, "place_id"),
                getString(cursor, "alias_text"),
                getNullableString(cursor, "alias_type")
        );
    }

    public static List<Checkpoint> toCheckpoints(Cursor cursor) {
        List<Checkpoint> results = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                results.add(toCheckpoint(cursor));
            }
            return results;
        } finally {
            cursor.close();
        }
    }

    public static List<Place> toPlaces(Cursor cursor) {
        List<Place> results = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                results.add(toPlace(cursor));
            }
            return results;
        } finally {
            cursor.close();
        }
    }

    public static List<Edge> toEdges(Cursor cursor) {
        List<Edge> results = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                results.add(toEdge(cursor));
            }
            return results;
        } finally {
            cursor.close();
        }
    }

    public static List<CrowdRule> toCrowdRules(Cursor cursor) {
        List<CrowdRule> results = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                results.add(toCrowdRule(cursor));
            }
            return results;
        } finally {
            cursor.close();
        }
    }

    public static List<OutdoorPano> toOutdoorPanos(Cursor cursor) {
        List<OutdoorPano> results = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                results.add(toOutdoorPano(cursor));
            }
            return results;
        } finally {
            cursor.close();
        }
    }

    public static List<RecognitionRef> toRecognitionRefs(Cursor cursor) {
        List<RecognitionRef> results = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                results.add(toRecognitionRef(cursor));
            }
            return results;
        } finally {
            cursor.close();
        }
    }

    public static List<SearchAlias> toSearchAliases(Cursor cursor) {
        List<SearchAlias> results = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                results.add(toSearchAlias(cursor));
            }
            return results;
        } finally {
            cursor.close();
        }
    }

    public static Checkpoint firstCheckpointOrNull(Cursor cursor) {
        try {
            return cursor.moveToFirst() ? toCheckpoint(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    public static Place firstPlaceOrNull(Cursor cursor) {
        try {
            return cursor.moveToFirst() ? toPlace(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    public static Edge firstEdgeOrNull(Cursor cursor) {
        try {
            return cursor.moveToFirst() ? toEdge(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    public static CrowdRule firstCrowdRuleOrNull(Cursor cursor) {
        try {
            return cursor.moveToFirst() ? toCrowdRule(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    public static OutdoorPano firstOutdoorPanoOrNull(Cursor cursor) {
        try {
            return cursor.moveToFirst() ? toOutdoorPano(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    public static RecognitionRef firstRecognitionRefOrNull(Cursor cursor) {
        try {
            return cursor.moveToFirst() ? toRecognitionRef(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    public static SearchAlias firstSearchAliasOrNull(Cursor cursor) {
        try {
            return cursor.moveToFirst() ? toSearchAlias(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    private static String getString(Cursor cursor, String columnName) {
        return cursor.getString(cursor.getColumnIndexOrThrow(columnName));
    }

    private static String getNullableString(Cursor cursor, String columnName) {
        int columnIndex = cursor.getColumnIndexOrThrow(columnName);
        return cursor.isNull(columnIndex) ? null : cursor.getString(columnIndex);
    }

    private static int getInt(Cursor cursor, String columnName) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(columnName));
    }

    private static double getDouble(Cursor cursor, String columnName) {
        return cursor.getDouble(cursor.getColumnIndexOrThrow(columnName));
    }

    private static Double getNullableDouble(Cursor cursor, String columnName) {
        int columnIndex = cursor.getColumnIndexOrThrow(columnName);
        return cursor.isNull(columnIndex) ? null : cursor.getDouble(columnIndex);
    }
}
