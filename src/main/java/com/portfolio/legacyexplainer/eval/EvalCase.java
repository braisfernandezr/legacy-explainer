package com.portfolio.legacyexplainer.eval;

import java.util.List;

/**
 * One evaluation case: a snippet the model is asked about, and the key
 * points a good answer must mention. Each entry in {@code expected} is a
 * list of synonyms — the point counts as covered if the answer contains
 * any of them (case-insensitive).
 */
public record EvalCase(
        String name,
        String snippet,
        String language,
        String mode,
        List<List<String>> expected
) {
}
