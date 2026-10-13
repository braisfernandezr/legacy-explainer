package com.portfolio.legacyexplainer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for the local Ollama backend. Bound from the
 * {@code app.ollama.*} keys in application.properties so the future
 * OllamaCodeExplainer only needs to inject this record.
 */
@ConfigurationProperties(prefix = "app.ollama")
public record OllamaProperties(
        String baseUrl,
        String model,
        Duration timeout
) {
    public OllamaProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://localhost:11434";
        if (model == null || model.isBlank()) model = "qwen2.5-coder:1.5b";
        if (timeout == null) timeout = Duration.ofMinutes(3);
    }
}
