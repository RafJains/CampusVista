package com.example.campusvista.ui.common;

import static org.junit.Assert.assertEquals;

import com.example.campusvista.data.model.Checkpoint;

import org.junit.Test;

import java.util.Arrays;

public final class UiTextTest {
    @Test
    public void checkpointNameFallsBackWhenCheckpointIsMissing() {
        assertEquals("OUT_CP001", UiText.checkpointName(null, "OUT_CP001"));
        assertEquals("Main Gate", UiText.checkpointName(checkpoint("Main Gate"), "OUT_CP001"));
    }

    @Test
    public void crowdWarningMessageIncludesOnlyProbableWarnings() {
        assertEquals(
                "Main gate may be crowded\n\nCafeteria may be busy",
                UiText.crowdWarningMessage(Arrays.asList(
                        "Main gate may be crowded",
                        "FYI only",
                        null,
                        "Cafeteria may be busy"
                ))
        );
    }

    private static Checkpoint checkpoint(String name) {
        return new Checkpoint("OUT_CP001", name, "outdoor", 0.0, 0.0,
                null, null, null, null, null, null);
    }
}
