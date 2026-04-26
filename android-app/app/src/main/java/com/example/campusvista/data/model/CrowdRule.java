package com.example.campusvista.data.model;

public final class CrowdRule {
    private final String crowdRuleId;
    private final String checkpointId;
    private final String dayType;
    private final String startTime;
    private final String endTime;
    private final String crowdLevel;
    private final double penaltyCost;
    private final String description;

    public CrowdRule(
            String crowdRuleId,
            String checkpointId,
            String dayType,
            String startTime,
            String endTime,
            String crowdLevel,
            double penaltyCost,
            String description
    ) {
        this.crowdRuleId = crowdRuleId;
        this.checkpointId = checkpointId;
        this.dayType = dayType;
        this.startTime = startTime;
        this.endTime = endTime;
        this.crowdLevel = crowdLevel;
        this.penaltyCost = penaltyCost;
        this.description = description;
    }

    public String getCrowdRuleId() {
        return crowdRuleId;
    }

    public String getCheckpointId() {
        return checkpointId;
    }

    public String getDayType() {
        return dayType;
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

    public double getPenaltyCost() {
        return penaltyCost;
    }

    public String getDescription() {
        return description;
    }
}
