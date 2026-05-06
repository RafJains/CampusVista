package com.example.campusvista.recognition;

import com.example.campusvista.network.BackendDtos.RecognitionMatchDto;

import java.util.List;

public final class RecognitionConfidence {
    private static final double MIN_CONFIDENCE_PERCENT = 70.0;
    private static final double MIN_MARGIN_PERCENT = 6.0;
    private static final int MIN_SUPPORTING_VIEWS = 2;

    private RecognitionConfidence() {
    }

    public static boolean isConfident(List<RecognitionMatchDto> matches) {
        if (matches == null || matches.isEmpty()) {
            return false;
        }
        RecognitionMatchDto top = matches.get(0);
        double second = matches.size() > 1 ? matches.get(1).confidencePercent : 0.0;
        return top.confidencePercent >= MIN_CONFIDENCE_PERCENT
                && top.confidencePercent - second >= MIN_MARGIN_PERCENT
                && top.supportingViews >= MIN_SUPPORTING_VIEWS;
    }
}
