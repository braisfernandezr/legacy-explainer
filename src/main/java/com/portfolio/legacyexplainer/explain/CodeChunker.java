package com.portfolio.legacyexplainer.explain;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits source code that is too large for the model's context window
 * into chunks that fit. Prefers to cut at blank lines or non-indented
 * lines (top-level declarations in most languages) so a chunk does not
 * start in the middle of a routine.
 */
@Component
public class CodeChunker {

    /**
     * @param code     the source code to split
     * @param maxChars maximum size of each chunk
     * @return the chunks, in order; a single chunk if the code already fits
     */
    public List<String> chunk(String code, int maxChars) {
        if (code.length() <= maxChars) {
            return List.of(code);
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int lastBoundary = 0; // offset in current where the most recent good cut point starts

        for (String line : code.split("\n", -1)) {
            if (current.length() > 0 && current.length() + 1 + line.length() > maxChars) {
                // A cut at the boundary keeps routines whole; fall back to cutting
                // here when the boundary would leave a uselessly small chunk.
                int cut = lastBoundary > maxChars / 3 ? lastBoundary : current.length();
                chunks.add(current.substring(0, cut).stripTrailing());
                current = new StringBuilder(current.substring(cut));
                lastBoundary = 0;
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            if (current.length() > 0 && isBoundaryLine(line)) {
                lastBoundary = current.length();
            }
            current.append(line);
        }
        if (!current.isEmpty() && !current.toString().isBlank()) {
            chunks.add(current.toString().stripTrailing());
        }

        return hardSplitOversized(chunks, maxChars);
    }

    private boolean isBoundaryLine(String line) {
        return line.isBlank() || !Character.isWhitespace(line.charAt(0));
    }

    /** A single line longer than maxChars (e.g. minified code) is split by size. */
    private List<String> hardSplitOversized(List<String> chunks, int maxChars) {
        List<String> result = new ArrayList<>(chunks.size());
        for (String chunk : chunks) {
            for (int i = 0; i < chunk.length(); i += maxChars) {
                result.add(chunk.substring(i, Math.min(chunk.length(), i + maxChars)));
            }
        }
        return result;
    }
}
