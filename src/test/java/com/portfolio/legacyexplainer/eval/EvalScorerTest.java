package com.portfolio.legacyexplainer.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvalScorerTest {

    @Test
    void countsCoveredAndMissedPoints() {
        EvalScorer.Score score = EvalScorer.score(
                "This program computes the OVERTIME pay and deducts tax.",
                List.of(
                        List.of("overtime"),
                        List.of("tax"),
                        List.of("net", "take-home")));

        assertEquals(2, score.covered());
        assertEquals(3, score.total());
        assertEquals(List.of("net", "take-home"), score.missed().get(0).anyOf());
    }

    @Test
    void anySynonymCountsCaseInsensitively() {
        EvalScorer.Score score = EvalScorer.score(
                "It returns the take-home amount.",
                List.of(List.of("net", "Take-Home")));

        assertEquals(1, score.covered());
    }
}
