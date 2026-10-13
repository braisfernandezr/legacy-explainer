package com.portfolio.legacyexplainer.explain;

import com.portfolio.legacyexplainer.api.dto.ExplainRequest;
import com.portfolio.legacyexplainer.api.dto.ExplainResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ExplainService {

    /**
     * Code above this size is analyzed in chunks. Sized so a single chunk
     * plus prompts and answer fits the model's num_ctx of 4096 tokens
     * (~3.5 chars per token for source code).
     */
    static final int CHUNK_THRESHOLD_CHARS = 7_000;
    static final int CHUNK_TARGET_CHARS = 6_000;

    /** Receives progress while an explanation is being generated. */
    public interface StreamListener {

        /** A phase change worth showing to the user ("Analyzing part 2 of 5…"). */
        void onStatus(String message);

        /** The next fragment of the answer text. */
        void onToken(String token);
    }

    private final CodeExplainer explainer;
    private final LanguageDetector languageDetector;
    private final CodeChunker chunker;

    public ExplainService(CodeExplainer explainer, LanguageDetector languageDetector, CodeChunker chunker) {
        this.explainer = explainer;
        this.languageDetector = languageDetector;
        this.chunker = chunker;
    }

    public ExplainResponse explain(ExplainRequest request) {
        return explain(request, null);
    }

    /**
     * Explains the code, reporting progress and answer tokens to
     * {@code listener} when one is given. Small code is a single LLM call;
     * large code is split into chunks, each chunk analyzed separately, and
     * the per-part results combined in a final call (only that final answer
     * is streamed as tokens).
     */
    public ExplainResponse explain(ExplainRequest request, StreamListener listener) {
        ExplainMode mode = request.mode() == null ? ExplainMode.EXPLAIN : request.mode();
        String language = normalizeLanguage(request, mode);

        long start = System.nanoTime();
        String explanation;
        int parts;
        if (request.code().length() <= CHUNK_THRESHOLD_CHARS) {
            parts = 1;
            explanation = generate(mode.systemPrompt(), mode.userPrompt(language, request.code()), listener);
        } else {
            List<String> chunks = chunker.chunk(request.code(), CHUNK_TARGET_CHARS);
            parts = chunks.size();
            List<String> partResults = new ArrayList<>(parts);
            for (int i = 0; i < parts; i++) {
                status(listener, "Analyzing part %d of %d…".formatted(i + 1, parts));
                partResults.add(explainer.generate(
                        mode.systemPrompt(),
                        mode.chunkPrompt(language, i + 1, parts, chunks.get(i))));
            }
            status(listener, "Combining the results of %d parts…".formatted(parts));
            explanation = generate(mode.systemPrompt(), mode.synthesisPrompt(language, partResults), listener);
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        return new ExplainResponse(explanation, language, explainer.modelName(), mode.id(), parts, durationMs);
    }

    private String generate(String systemPrompt, String userPrompt, StreamListener listener) {
        if (listener == null) {
            return explainer.generate(systemPrompt, userPrompt);
        }
        StringBuilder answer = new StringBuilder();
        explainer.generateStreaming(systemPrompt, userPrompt, token -> {
            answer.append(token);
            listener.onToken(token);
        });
        return answer.toString().strip();
    }

    private void status(StreamListener listener, String message) {
        if (listener != null) {
            listener.onStatus(message);
        }
    }

    private String normalizeLanguage(ExplainRequest request, ExplainMode mode) {
        String language = request.language();
        if (language == null || language.isBlank() || "auto".equalsIgnoreCase(language)) {
            String code = mode == ExplainMode.DIFF ? stripDiffMarkers(request.code()) : request.code();
            return languageDetector.detect(code);
        }
        return language.trim();
    }

    /**
     * Reduces a unified diff to something resembling the underlying code, so
     * the language heuristics see {@code PERFORM ...} instead of {@code +PERFORM ...}.
     */
    private static String stripDiffMarkers(String diff) {
        StringBuilder code = new StringBuilder();
        for (String line : diff.split("\n", -1)) {
            if (line.startsWith("diff ") || line.startsWith("index ") || line.startsWith("@@")
                    || line.startsWith("--- ") || line.startsWith("+++ ")
                    || line.startsWith("new file") || line.startsWith("deleted file")) {
                continue;
            }
            if (!line.isEmpty() && (line.charAt(0) == '+' || line.charAt(0) == '-' || line.charAt(0) == ' ')) {
                line = line.substring(1);
            }
            code.append(line).append('\n');
        }
        return code.toString();
    }
}
