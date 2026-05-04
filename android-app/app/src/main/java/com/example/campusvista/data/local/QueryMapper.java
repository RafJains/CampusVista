package com.example.campusvista.data.local;

import android.database.Cursor;

import com.example.campusvista.data.model.Checkpoint;
import com.example.campusvista.data.model.CrowdRule;
import com.example.campusvista.data.model.Edge;
import com.example.campusvista.data.model.OutdoorPano;
import com.example.campusvista.data.model.Place;

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
                getNullableDoubleIfPresent(cursor, "raw_map_x"),
                getNullableDoubleIfPresent(cursor, "raw_map_y"),
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
                getString(cursor, "start_time"),
                getString(cursor, "end_time"),
                getString(cursor, "crowd_level")
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

    public static List<Checkpoint> toCheckpoints(Cursor cursor) {
        return toList(cursor, QueryMapper::toCheckpoint);
    }

    public static List<Place> toPlaces(Cursor cursor) {
        return toList(cursor, QueryMapper::toPlace);
    }

    public static List<Edge> toEdges(Cursor cursor) {
        return toList(cursor, QueryMapper::toEdge);
    }

    public static Checkpoint firstCheckpointOrNull(Cursor cursor) {
        return firstOrNull(cursor, QueryMapper::toCheckpoint);
    }

    public static Place firstPlaceOrNull(Cursor cursor) {
        return firstOrNull(cursor, QueryMapper::toPlace);
    }

    public static CrowdRule firstCrowdRuleOrNull(Cursor cursor) {
        return firstOrNull(cursor, QueryMapper::toCrowdRule);
    }

    public static OutdoorPano firstOutdoorPanoOrNull(Cursor cursor) {
        return firstOrNull(cursor, QueryMapper::toOutdoorPano);
    }

    private static <T> List<T> toList(Cursor cursor, CursorMapper<T> mapper) {
        List<T> results = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                results.add(mapper.map(cursor));
            }
            return results;
        } finally {
            cursor.close();
        }
    }

    private static <T> T firstOrNull(Cursor cursor, CursorMapper<T> mapper) {
        try {
            return cursor.moveToFirst() ? mapper.map(cursor) : null;
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

    private static Double getNullableDoubleIfPresent(Cursor cursor, String columnName) {
        int columnIndex = cursor.getColumnIndex(columnName);
        if (columnIndex < 0 || cursor.isNull(columnIndex)) {
            return null;
        }
        return cursor.getDouble(columnIndex);
    }

    private interface CursorMapper<T> {
        T map(Cursor cursor);
    }
}
