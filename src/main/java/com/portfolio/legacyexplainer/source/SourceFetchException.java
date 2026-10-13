package com.portfolio.legacyexplainer.source;

/** A source URL could not be used: unsupported, unreachable, or not text. */
public class SourceFetchException extends RuntimeException {

    public SourceFetchException(String message) {
        super(message);
    }
}
