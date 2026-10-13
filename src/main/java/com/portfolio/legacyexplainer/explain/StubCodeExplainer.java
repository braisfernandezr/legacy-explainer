package com.portfolio.legacyexplainer.explain;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * No-LLM fallback, useful for demoing the UI and for tests. Streams its
 * canned answer word by word so the streaming path can be exercised
 * without Ollama. Activate with {@code app.explainer=stub}.
 */
@Component
@ConditionalOnProperty(name = "app.explainer", havingValue = "stub", matchIfMissing = true)
public class StubCodeExplainer implements CodeExplainer {

    private static final long TOKEN_DELAY_MS = 10;

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        return """
                [Stub explanation — no LLM configured]

                This placeholder answer was produced by the stub backend for a prompt of \
                %d characters. Set app.explainer=ollama in application.properties to get \
                real explanations from a local model."""
                .formatted(userPrompt.length());
    }

    @Override
    public void generateStreaming(String systemPrompt, String userPrompt, Consumer<String> onToken) {
        for (String word : generate(systemPrompt, userPrompt).split("(?<= )")) {
            onToken.accept(word);
            try {
                Thread.sleep(TOKEN_DELAY_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override
    public String modelName() {
        return "stub";
    }
}
