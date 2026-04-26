package com.example.campusvista.data.model;

public final class Place {
    private final String placeId;
    private final String placeName;
    private final String placeType;
    private final String checkpointId;
    private final String description;
    private final String keywords;

    public Place(
            String placeId,
            String placeName,
            String placeType,
            String checkpointId,
            String description,
            String keywords
    ) {
        this.placeId = placeId;
        this.placeName = placeName;
        this.placeType = placeType;
        this.checkpointId = checkpointId;
        this.description = description;
        this.keywords = keywords;
    }

    public String getPlaceId() {
        return placeId;
    }

    public String getPlaceName() {
        return placeName;
    }

    public String getPlaceType() {
        return placeType;
    }

    public String getCheckpointId() {
        return checkpointId;
    }

    public String getDescription() {
        return description;
    }

    public String getKeywords() {
        return keywords;
    }
}
