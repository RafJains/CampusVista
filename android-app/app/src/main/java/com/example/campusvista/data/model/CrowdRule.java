package com.example.campusvista.data.model;

public final class CrowdRule {
    private final String startTime;
    private final String endTime;
    private final String crowdLevel;

    public CrowdRule(
            String startTime,
            String endTime,
            String crowdLevel
    ) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.crowdLevel = crowdLevel;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public String getCrowdLevel() {
        return crowdLevel;
    }
}
