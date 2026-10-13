package com.portfolio.legacyexplainer.api.dto;

import com.portfolio.legacyexplainer.explain.ExplainMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExplainRequest(
        @NotBlank(message = "Code must not be empty")
        @Size(max = 60_000, message = "Code must not exceed 60,000 characters")
        String code,

        String language,

        ExplainMode mode
) {
}
