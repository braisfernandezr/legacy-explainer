package com.portfolio.legacyexplainer.explain;

import java.util.function.Consumer;

/**
 * Abstraction over the LLM backend. Implementations receive ready-made
 * prompts (built by {@link ExplainMode}) so they only deal with talking
 * to the model, not with what is being asked of it.
 */
public interface CodeExplainer {

    /**
     * Generates a complete answer in one call.
     *
     * @param systemPrompt instructions defining the model's task
     * @param userPrompt   the request, including the code to analyze
     * @return the model's answer
     */
    String generate(String systemPrompt, String userPrompt);

    /**
     * Generates an answer as a stream of tokens, invoking {@code onToken}
     * for each fragment as the model produces it. Returns when the model
     * is done. Exceptions thrown by {@code onToken} abort the generation
     * and propagate to the caller.
     */
    void generateStreaming(String systemPrompt, String userPrompt, Consumer<String> onToken);

    /** Name of the model producing the answer, for display purposes. */
    String modelName();
}
