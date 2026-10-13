package com.portfolio.legacyexplainer.api.dto;

public record ExplainResponse(
        String explanation,
        String detectedLanguage,
        String model,
        String mode,
        int parts,
        long durationMs
) {
}
