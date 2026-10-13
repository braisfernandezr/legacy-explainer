package com.portfolio.legacyexplainer.eval;

import java.util.List;
import java.util.Locale;

/** Scores an explanation against a case's expected key points. */
public final class EvalScorer {

    /** One expected point and whether the explanation covered it. */
    public record PointResult(List<String> anyOf, boolean covered) {
    }

    public record Score(List<PointResult> points) {

        public long covered() {
            return points.stream().filter(PointResult::covered).count();
        }

        public int total() {
            return points.size();
        }

        public List<PointResult> missed() {
            return points.stream().filter(p -> !p.covered()).toList();
        }
    }

    private EvalScorer() {
    }

    public static Score score(String explanation, List<List<String>> expected) {
        String haystack = explanation.toLowerCase(Locale.ROOT);
        List<PointResult> points = expected.stream()
                .map(synonyms -> new PointResult(synonyms, synonyms.stream()
                        .anyMatch(s -> haystack.contains(s.toLowerCase(Locale.ROOT)))))
                .toList();
        return new Score(points);
    }
}
