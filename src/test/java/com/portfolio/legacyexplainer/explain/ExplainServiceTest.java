package com.portfolio.legacyexplainer.explain;

import com.portfolio.legacyexplainer.api.dto.ExplainRequest;
import com.portfolio.legacyexplainer.api.dto.ExplainResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplainServiceTest {

    private final ExplainService service = new ExplainService(
            new StubCodeExplainer(), new LanguageDetector(), new CodeChunker());

    private record CollectingListener(List<String> statuses, StringBuilder tokens)
            implements ExplainService.StreamListener {

        CollectingListener() {
            this(new ArrayList<>(), new StringBuilder());
        }

        @Override
        public void onStatus(String message) {
            statuses.add(message);
        }

        @Override
        public void onToken(String token) {
            tokens.append(token);
        }
    }

    @Test
    void streamsTokensThatAddUpToTheFinalExplanation() {
        CollectingListener listener = new CollectingListener();

        ExplainResponse response = service.explain(
                new ExplainRequest("int main() { return 0; }", "C", null), listener);

        assertEquals(response.explanation(), listener.tokens().toString().strip());
        assertEquals(1, response.parts());
        assertEquals("explain", response.mode());
        assertTrue(listener.statuses().isEmpty(), "no chunking statuses expected for small code");
    }

    @Test
    void chunksLargeCodeAndReportsProgress() {
        String code = "line of legacy code;\n".repeat(600); // ~12,600 chars → several chunks
        CollectingListener listener = new CollectingListener();

        ExplainResponse response = service.explain(
                new ExplainRequest(code, "COBOL", ExplainMode.SUMMARIZE), listener);

        assertTrue(response.parts() > 1, "expected the code to be split into parts");
        assertEquals("summarize", response.mode());
        assertFalse(response.explanation().isBlank());
        assertTrue(listener.statuses().stream().anyMatch(s -> s.contains("part 1 of")),
                "expected per-part progress, got: " + listener.statuses());
        assertTrue(listener.statuses().stream().anyMatch(s -> s.startsWith("Combining")),
                "expected a synthesis status, got: " + listener.statuses());
    }

    @Test
    void detectsLanguageThroughDiffMarkersInDiffMode() {
        String diff = """
                --- a/payroll.cbl
                +++ b/payroll.cbl
                @@ -1,3 +1,3 @@
                 PROCEDURE DIVISION.
                -    COMPUTE WS-GROSS = WS-HOURS * WS-RATE.
                +    COMPUTE WS-GROSS = WS-HOURS * WS-RATE * 1.5.
                """;

        ExplainResponse response = service.explain(
                new ExplainRequest(diff, "auto", ExplainMode.DIFF));

        assertEquals("COBOL", response.detectedLanguage());
        assertEquals("diff", response.mode());
    }

    @Test
    void defaultsToExplainModeWithoutListener() {
        ExplainResponse response = service.explain(
                new ExplainRequest("DISPLAY 'X'.", "COBOL", null));

        assertEquals("explain", response.mode());
        assertEquals(1, response.parts());
        assertFalse(response.explanation().isBlank());
    }
}
