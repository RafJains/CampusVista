package com.example.campusvista.recognition;

import java.util.List;

public final class RecognitionResponse {
    public boolean recognized;
    public List<RecognitionMatch> matches;
    public String message;
    public String modelVersion;
}
