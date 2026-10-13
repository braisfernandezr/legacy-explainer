package com.portfolio.legacyexplainer.explain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeChunkerTest {

    private final CodeChunker chunker = new CodeChunker();

    @Test
    void smallCodeIsASingleChunk() {
        String code = "int main() {\n    return 0;\n}";
        assertEquals(List.of(code), chunker.chunk(code, 1000));
    }

    @Test
    void splitsLargeCodeAtRoutineBoundaries() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            code.append("void method").append(i).append("() {\n")
                    .append("    doSomething();\n")
                    .append("    doSomethingElse();\n")
                    .append("}\n\n");
        }

        List<String> chunks = chunker.chunk(code.toString(), 300);

        assertTrue(chunks.size() > 1, "expected multiple chunks");
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 300, "chunk exceeds max size: " + chunk.length());
            assertFalse(chunk.isBlank(), "blank chunk emitted");
            // Boundary-aware split: no chunk starts mid-routine.
            assertTrue(chunk.stripLeading().startsWith("void method"),
                    "chunk does not start at a routine boundary:\n" + chunk);
        }

        String rejoined = String.join("\n", chunks);
        for (int i = 0; i < 30; i++) {
            String marker = "void method" + i + "()";
            int first = rejoined.indexOf(marker);
            assertTrue(first >= 0, "lost content: " + marker);
            assertEquals(first, rejoined.lastIndexOf(marker), "duplicated content: " + marker);
        }
    }

    @Test
    void hardSplitsASingleOversizedLine() {
        String code = "x".repeat(2500);
        List<String> chunks = chunker.chunk(code, 1000);
        assertEquals(3, chunks.size());
        assertEquals(code, String.join("", chunks));
    }
}
