package com.portfolio.legacyexplainer.source;

public record FetchedSource(
        String code,
        String fileName,
        boolean truncated
) {
}
