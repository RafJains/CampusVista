package com.example.campusvista.recognition;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.campusvista.network.BackendDtos.RecognitionMatchDto;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class RecognitionConfidenceTest {
    @Test
    public void confidentWhenTopMatchHasEnoughScoreMarginAndSupport() {
        assertTrue(RecognitionConfidence.isConfident(Arrays.asList(
                match(82.0, 3),
                match(70.0, 4)
        )));
    }

    @Test
    public void notConfidentWhenEmptyWeakCloseOrUnderSupported() {
        assertFalse(RecognitionConfidence.isConfident(Collections.emptyList()));
        assertFalse(RecognitionConfidence.isConfident(Collections.singletonList(match(69.9, 3))));
        assertFalse(RecognitionConfidence.isConfident(Arrays.asList(
                match(82.0, 3),
                match(78.0, 4)
        )));
        assertFalse(RecognitionConfidence.isConfident(Collections.singletonList(match(82.0, 1))));
    }

    private static RecognitionMatchDto match(double confidencePercent, int supportingViews) {
        RecognitionMatchDto match = new RecognitionMatchDto();
        match.confidencePercent = confidencePercent;
        match.supportingViews = supportingViews;
        return match;
    }
}
