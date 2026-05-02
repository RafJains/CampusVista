package com.example.campusvista.routing;

import android.content.Context;

import com.example.campusvista.data.model.CrowdRule;
import com.example.campusvista.data.model.Checkpoint;
import com.example.campusvista.data.repository.CrowdRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CrowdCostCalculator {
    private static CrowdCostCalculator instance;
    private final CrowdRepository crowdRepository;

    public static synchronized CrowdCostCalculator getInstance(Context context) {
        if (instance == null) {
            instance = new CrowdCostCalculator(CrowdRepository.getInstance(context));
        }
        return instance;
    }

    public CrowdCostCalculator(CrowdRepository crowdRepository) {
        this.crowdRepository = crowdRepository;
    }

    public Map<String, Double> getCurrentPenaltyByCheckpoint() {
        return Collections.emptyMap();
    }

    public Map<String, Double> getPenaltyByCheckpoint(Calendar now) {
        return Collections.emptyMap();
    }

    public double getPenaltyForCheckpoint(String checkpointId) {
        return 0.0;
    }

    public double calculateEdgeCost(
            Graph.DirectedEdge edge,
            RouteMode routeMode,
            Map<String, Double> penaltyByCheckpoint
    ) {
        return edge.getDistanceMeters();
    }

    public List<String> getCurrentWarningsForCheckpoints(List<Checkpoint> checkpoints) {
        if (checkpoints == null || checkpoints.isEmpty()) {
            return Collections.emptyList();
        }
        Calendar now = Calendar.getInstance();
        String dayType = getDayType(now);
        String currentTime = formatTime(now);
        List<String> warnings = new ArrayList<>();
        for (Checkpoint checkpoint : checkpoints) {
            CrowdRule rule = crowdRepository.getActiveCrowdRuleForCheckpoint(
                    checkpoint.getCheckpointId(),
                    dayType,
                    currentTime
            );
            if (rule != null) {
                warnings.add(checkpoint.getCheckpointName()
                        + " may be " + rule.getCrowdLevel().replace("_", " ")
                        + " between " + rule.getStartTime()
                        + " and " + rule.getEndTime() + ".");
            }
        }
        return warnings;
    }

    public static String getDayType(Calendar calendar) {
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            return "weekend";
        }
        return "weekday";
    }

    public static String formatTime(Calendar calendar) {
        return new SimpleDateFormat("HH:mm", Locale.US).format(calendar.getTime());
    }
}
