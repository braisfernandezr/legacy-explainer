package com.portfolio.legacyexplainer.explain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * What the user wants done with the code. Each mode carries the prompts
 * that shape the LLM's answer, both for a single call and for the
 * chunked pipeline used on large files.
 */
public enum ExplainMode {

    EXPLAIN("explain",
            """
            You are an expert in legacy software. Explain in plain language what the given \
            code does: its overall purpose, its inputs and outputs, and the important logic \
            step by step. Do not repeat the code back. Be clear and concise.""",
            "Explain what this %s code does",
            "Explain in plain language what the whole file does: its overall purpose, "
                    + "its inputs and outputs, and how the parts work together."),

    SUMMARIZE("summarize",
            """
            You are an expert in legacy software. Summarize the given code in one short \
            paragraph: what it is and what it is for. No step-by-step walkthrough. \
            Do not repeat the code back.""",
            "Summarize this %s code",
            "Write one short paragraph summarizing what the whole file is and what it is for."),

    BUGS("bugs",
            """
            You are an expert reviewer of legacy code. Identify potential bugs, unhandled \
            edge cases, and risky or obsolete constructs in the given code. List each \
            finding briefly, with why it is a problem. If nothing stands out, say so. \
            Do not repeat the code back.""",
            "Review this %s code for potential bugs and risks",
            "Produce a single list of the potential bugs and risks in the whole file, "
                    + "merging duplicate findings."),

    REFACTOR("refactor",
            """
            You are an expert in modernizing legacy software. Suggest how to refactor the \
            given code: clearer structure, safer constructs, modern equivalents. Give a \
            short list of concrete suggestions, each with its benefit. Do not rewrite the \
            whole program and do not repeat the code back.""",
            "Suggest how to refactor and modernize this %s code",
            "Produce a single list of the most valuable refactoring suggestions for the "
                    + "whole file, merging duplicates."),

    DIFF("diff",
            """
            You are an expert reviewer of changes to legacy software. You are given a diff \
            (patch). Explain what the change does: what was added, removed or modified, \
            the apparent intent behind it, and any risks or behavior changes it could \
            introduce. Do not repeat the diff back. Be clear and concise.""",
            "Explain what this diff of %s code changes",
            "Explain what the change as a whole does: its intent, the main modifications, "
                    + "and any risks or behavior changes it could introduce.");

    private final String id;
    private final String systemPrompt;
    private final String requestPhrase;
    private final String synthesisInstruction;

    ExplainMode(String id, String systemPrompt, String requestPhrase, String synthesisInstruction) {
        this.id = id;
        this.systemPrompt = systemPrompt;
        this.requestPhrase = requestPhrase;
        this.synthesisInstruction = synthesisInstruction;
    }

    @JsonValue
    public String id() {
        return id;
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public String userPrompt(String language, String code) {
        return "%s:\n\n```\n%s\n```".formatted(requestPhrase.formatted(language), code);
    }

    /** Prompt for one part of a large file that was split into chunks. */
    public String chunkPrompt(String language, int part, int totalParts, String code) {
        return ("This is part %d of %d of a larger file; the other parts are analyzed "
                + "separately. %s, keeping the answer short:\n\n```\n%s\n```")
                .formatted(part, totalParts, requestPhrase.formatted(language), code);
    }

    /** Prompt that combines the per-part results into one answer for the whole file. */
    public String synthesisPrompt(String language, List<String> partResults) {
        StringBuilder prompt = new StringBuilder(
                "A %s file was analyzed in %d parts. These are the results per part:\n\n"
                        .formatted(language, partResults.size()));
        for (int i = 0; i < partResults.size(); i++) {
            prompt.append("=== Part ").append(i + 1).append(" ===\n")
                    .append(partResults.get(i)).append("\n\n");
        }
        prompt.append("Combine these into one coherent answer for the whole file. ")
                .append(synthesisInstruction);
        return prompt.toString();
    }

    @JsonCreator
    public static ExplainMode fromId(String value) {
        for (ExplainMode mode : values()) {
            if (mode.id.equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown mode '" + value + "' (use "
                + Arrays.stream(values()).map(ExplainMode::id).collect(Collectors.joining(", ")) + ")");
    }
}
