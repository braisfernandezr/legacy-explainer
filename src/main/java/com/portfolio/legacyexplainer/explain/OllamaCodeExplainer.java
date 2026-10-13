package com.portfolio.legacyexplainer.explain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.ObjectMapper;
import com.portfolio.legacyexplainer.config.OllamaProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Explains code by calling a local Ollama instance
 * ({@code POST /api/generate}, non-streaming or NDJSON streaming).
 */
@Component
@ConditionalOnProperty(name = "app.explainer", havingValue = "ollama")
public class OllamaCodeExplainer implements CodeExplainer {

    private final RestClient restClient;
    private final OllamaProperties properties;
    private final ObjectMapper objectMapper;

    public OllamaCodeExplainer(OllamaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        try {
            GenerateResponse response = restClient.post()
                    .uri("/api/generate")
                    .body(generateRequest(systemPrompt, userPrompt, false))
                    .retrieve()
                    .body(GenerateResponse.class);
            if (response == null || response.response() == null || response.response().isBlank()) {
                throw new ExplainerUnavailableException("Ollama returned an empty response", null);
            }
            return response.response().strip();
        } catch (ResourceAccessException ex) {
            throw cannotReach(ex);
        } catch (RestClientException ex) {
            throw requestFailed(ex);
        }
    }

    @Override
    public void generateStreaming(String systemPrompt, String userPrompt, Consumer<String> onToken) {
        try {
            restClient.post()
                    .uri("/api/generate")
                    .body(generateRequest(systemPrompt, userPrompt, true))
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new ExplainerUnavailableException(
                                    "Ollama returned HTTP " + response.getStatusCode().value()
                                            + " (model '" + properties.model() + "' pulled?)", null);
                        }
                        readNdjsonStream(response.getBody() == null ? null
                                : new BufferedReader(new InputStreamReader(
                                        response.getBody(), StandardCharsets.UTF_8)), onToken);
                        return null;
                    });
        } catch (ResourceAccessException ex) {
            throw cannotReach(ex);
        } catch (RestClientException ex) {
            throw requestFailed(ex);
        }
    }

    /**
     * Ollama streams one JSON object per line: {@code {"response":"tok","done":false}}
     * repeatedly, then a final line with {@code "done":true}.
     */
    private void readNdjsonStream(BufferedReader reader, Consumer<String> onToken) throws java.io.IOException {
        if (reader == null) {
            throw new ExplainerUnavailableException("Ollama returned an empty response", null);
        }
        boolean gotAnything = false;
        try (reader) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                GenerateResponse part = objectMapper.readValue(line, GenerateResponse.class);
                if (part.error() != null) {
                    throw new ExplainerUnavailableException("Ollama error: " + part.error(), null);
                }
                if (part.response() != null && !part.response().isEmpty()) {
                    onToken.accept(part.response());
                    gotAnything = true;
                }
                if (Boolean.TRUE.equals(part.done())) {
                    break;
                }
            }
        }
        if (!gotAnything) {
            throw new ExplainerUnavailableException("Ollama returned an empty response", null);
        }
    }

    private Map<String, Object> generateRequest(String systemPrompt, String userPrompt, boolean stream) {
        return Map.of(
                "model", properties.model(),
                "system", systemPrompt,
                "prompt", userPrompt,
                "stream", stream,
                "options", Map.of(
                        "temperature", 0.2,
                        "num_ctx", 4096
                )
        );
    }

    private ExplainerUnavailableException cannotReach(ResourceAccessException ex) {
        return new ExplainerUnavailableException(
                "Cannot reach Ollama at " + properties.baseUrl() + " — is it running?", ex);
    }

    private ExplainerUnavailableException requestFailed(RestClientException ex) {
        return new ExplainerUnavailableException(
                "Ollama request failed (model '" + properties.model() + "' pulled?): " + ex.getMessage(), ex);
    }

    @Override
    public String modelName() {
        return properties.model();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GenerateResponse(String response, Boolean done, String error) {
    }
}
