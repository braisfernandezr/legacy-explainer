package com.portfolio.legacyexplainer.explain;

/** Thrown when the LLM backend cannot be reached or fails to respond. */
public class ExplainerUnavailableException extends RuntimeException {

    public ExplainerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
