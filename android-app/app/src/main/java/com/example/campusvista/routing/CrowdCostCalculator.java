package com.example.campusvista.routing;

import android.content.Context;

import com.example.campusvista.data.model.CrowdRule;
import com.example.campusvista.data.repository.CrowdRepository;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
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
        return getPenaltyByCheckpoint(Calendar.getInstance());
    }

    public Map<String, Double> getPenaltyByCheckpoint(Calendar now) {
        String dayType = getDayType(now);
        String currentTime = formatTime(now);
        List<CrowdRule> activeRules = crowdRepository.getActiveCrowdRules(dayType, currentTime);
        Map<String, Double> penalties = new HashMap<>();

        for (CrowdRule rule : activeRules) {
            Double previousPenalty = penalties.get(rule.getCheckpointId());
            if (previousPenalty == null || rule.getPenaltyCost() > previousPenalty) {
                penalties.put(rule.getCheckpointId(), rule.getPenaltyCost());
            }
        }

        return Collections.unmodifiableMap(penalties);
    }

    public double getPenaltyForCheckpoint(String checkpointId) {
        Double penalty = getCurrentPenaltyByCheckpoint().get(checkpointId);
        return penalty == null ? 0.0 : penalty;
    }

    public double calculateEdgeCost(
            Graph.DirectedEdge edge,
            RouteMode routeMode,
            Map<String, Double> penaltyByCheckpoint
    ) {
        if (routeMode != RouteMode.AVOID_CROWDED_PATH) {
            return edge.getDistanceMeters();
        }
        Double penalty = penaltyByCheckpoint.get(edge.getToCheckpointId());
        return edge.getDistanceMeters() + (penalty == null ? 0.0 : penalty);
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
