package com.portfolio.legacyexplainer.eval;

import com.portfolio.legacyexplainer.api.dto.ExplainRequest;
import com.portfolio.legacyexplainer.api.dto.ExplainResponse;
import com.portfolio.legacyexplainer.explain.ExplainMode;
import com.portfolio.legacyexplainer.explain.ExplainService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluation harness: runs every case in {@code eval/cases.json} through
 * the real pipeline and scores whether the model's answer mentions the
 * expected key points. Activated by the {@code eval} Spring profile,
 * which also disables the web server, so the app runs the suite and exits:
 *
 * <pre>.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=eval"</pre>
 *
 * Prints a summary table and writes the full report to {@code eval-report.md}.
 */
@Component
@Profile("eval")
public class EvalRunner implements ApplicationRunner {

    private record EvalSuite(List<EvalCase> cases) {
    }

    private record CaseResult(EvalCase evalCase, ExplainResponse response, EvalScorer.Score score) {
    }

    private final ExplainService explainService;
    private final ObjectMapper objectMapper;

    public EvalRunner(ExplainService explainService, ObjectMapper objectMapper) {
        this.explainService = explainService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<EvalCase> cases = loadCases();
        System.out.printf("%nRunning %d eval cases (each is a real LLM call — expect a few minutes on CPU)%n%n", cases.size());

        List<CaseResult> results = new ArrayList<>();
        for (EvalCase evalCase : cases) {
            System.out.printf("  %-18s ... ", evalCase.name());
            System.out.flush();
            CaseResult result = runCase(evalCase);
            results.add(result);
            System.out.printf("%d/%d points, %.1f s%n",
                    result.score().covered(), result.score().total(),
                    result.response().durationMs() / 1000.0);
        }

        String report = buildReport(results);
        Path reportFile = Path.of("eval-report.md");
        Files.writeString(reportFile, report, StandardCharsets.UTF_8);

        long covered = results.stream().mapToLong(r -> r.score().covered()).sum();
        long total = results.stream().mapToLong(r -> r.score().total()).sum();
        System.out.printf("%nOverall: %d/%d points (%.0f%%) — full report in %s%n",
                covered, total, 100.0 * covered / total, reportFile.toAbsolutePath());
    }

    private List<EvalCase> loadCases() throws Exception {
        try (InputStream in = new ClassPathResource("eval/cases.json").getInputStream()) {
            return objectMapper.readValue(in, EvalSuite.class).cases();
        }
    }

    private CaseResult runCase(EvalCase evalCase) throws Exception {
        String code;
        try (InputStream in = new ClassPathResource("eval/snippets/" + evalCase.snippet()).getInputStream()) {
            code = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        ExplainMode mode = evalCase.mode() == null ? ExplainMode.EXPLAIN : ExplainMode.fromId(evalCase.mode());
        ExplainResponse response = explainService.explain(new ExplainRequest(code, evalCase.language(), mode));
        return new CaseResult(evalCase, response, EvalScorer.score(response.explanation(), evalCase.expected()));
    }

    private String buildReport(List<CaseResult> results) {
        StringBuilder md = new StringBuilder();
        String model = results.isEmpty() ? "?" : results.get(0).response().model();
        md.append("# Eval report — ").append(model).append(" — ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n\n");

        md.append("| Case | Mode | Language | Score | Missed points | Time |\n");
        md.append("|---|---|---|---|---|---|\n");
        for (CaseResult r : results) {
            String missed = r.score().missed().stream()
                    .map(p -> String.join(" / ", p.anyOf()))
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("—");
            md.append("| %s | %s | %s | %d/%d | %s | %.1f s |%n".formatted(
                    r.evalCase().name(), r.response().mode(), r.response().detectedLanguage(),
                    r.score().covered(), r.score().total(), missed,
                    r.response().durationMs() / 1000.0));
        }

        long covered = results.stream().mapToLong(r -> r.score().covered()).sum();
        long total = results.stream().mapToLong(r -> r.score().total()).sum();
        md.append("%n**Overall: %d/%d points (%.0f%%)**%n".formatted(covered, total, 100.0 * covered / total));

        md.append("\n## Answers\n");
        for (CaseResult r : results) {
            md.append("\n### ").append(r.evalCase().name()).append("\n\n");
            md.append("> ").append(r.response().explanation().replace("\n", "\n> ")).append("\n");
        }
        return md.toString();
    }
}
