package com.example.campusvista.data.model;

public final class SearchAlias {
    private final String aliasId;
    private final String placeId;
    private final String aliasText;
    private final String aliasType;

    public SearchAlias(
            String aliasId,
            String placeId,
            String aliasText,
            String aliasType
    ) {
        this.aliasId = aliasId;
        this.placeId = placeId;
        this.aliasText = aliasText;
        this.aliasType = aliasType;
    }

    public String getAliasId() {
        return aliasId;
    }

    public String getPlaceId() {
        return placeId;
    }

    public String getAliasText() {
        return aliasText;
    }

    public String getAliasType() {
        return aliasType;
    }
}
