package com.example.campusvista.data.model;

public final class OutdoorPano {
    private final String panoId;
    private final String checkpointId;
    private final String imageFile;
    private final String thumbnailFile;
    private final String orientation;
    private final String description;

    public OutdoorPano(
            String panoId,
            String checkpointId,
            String imageFile,
            String thumbnailFile,
            String orientation,
            String description
    ) {
        this.panoId = panoId;
        this.checkpointId = checkpointId;
        this.imageFile = imageFile;
        this.thumbnailFile = thumbnailFile;
        this.orientation = orientation;
        this.description = description;
    }

    public String getPanoId() {
        return panoId;
    }

    public String getCheckpointId() {
        return checkpointId;
    }

    public String getImageFile() {
        return imageFile;
    }

    public String getThumbnailFile() {
        return thumbnailFile;
    }

    public String getOrientation() {
        return orientation;
    }

    public String getDescription() {
        return description;
    }
}
