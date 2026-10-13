# Legacy Code Explainer

Paste undocumented legacy code — COBOL, pre-generics Java, Fortran, Pascal… — and get a plain-language explanation of what it does, generated **entirely on your own machine** by a local LLM. No code ever leaves your computer, which matters when the legacy code in question is proprietary.

Built with **Spring Boot 4** and **Ollama**, designed to run on modest hardware (CPU-only, 8 GB RAM).

**Features**

- **Live streaming** — the answer types itself out token by token over Server-Sent Events instead of a long silent wait.
- **Five modes** — explain, summarize, find potential bugs, suggest refactorings, or explain a diff; each is a different prompt over the same pipeline.
- **Diff explainer** — paste a unified diff (auto-detected on paste) and get what changed, the apparent intent, and the risks.
- **Large-file chunking** — files too big for the model's context window are split at routine boundaries, analyzed part by part (with live progress), and the per-part results are combined into one answer.
- **Language auto-detection** — regex heuristics recognize COBOL, Fortran, RPG, PL/SQL and friends.
- **GitHub ingestion** — paste a github.com file link, raw URL, or gist and the app fetches the code for you (GitHub hosts only, size-capped, binary-rejecting).
- **Evaluation harness** — a scored test suite that checks whether the model's answers actually mention the key points they should (`eval` profile).

## How it works

```
Browser (static HTML/JS)
   │  POST /api/explain/stream  { code, language, mode }   (SSE)
   ▼
Spring Boot API
   ├── LanguageDetector      regex heuristics guess the language when set to "auto"
   ├── ExplainMode           per-mode prompts (explain / summarize / bugs / refactor)
   ├── CodeChunker           splits large files at routine boundaries
   ├── ExplainService        orchestrates detection + chunking + LLM calls
   └── OllamaCodeExplainer   POST /api/generate on the local Ollama server
                             (non-streaming per chunk, NDJSON streaming for the answer)
```

For code that fits the model's context window (≤ 7,000 chars) the service makes a single streamed LLM call. Above that, it chunks the code (~6,000 chars per chunk, cutting at blank or non-indented lines), analyzes each chunk in its own call, then streams a final call that merges the per-part results — a map-reduce over the file.

The LLM backend sits behind a `CodeExplainer` interface, so swapping models or providers means one new class and one config line. A `StubCodeExplainer` implementation keeps the app (and its tests) runnable with no LLM at all — it even fakes token streaming.

## Quick start

Requires Java 17+ and [Ollama](https://ollama.com/download). No Maven install needed — the wrapper is included.

```
ollama pull qwen2.5-coder:1.5b
.\mvnw.cmd spring-boot:run          # ./mvnw spring-boot:run on Linux/macOS
```

Open **http://localhost:8080**, paste code, leave the language on auto-detect and press **Explain** (or Ctrl+Enter).

Expect 10–60 seconds per explanation on a CPU-only machine, depending on code length. The first request after a pause is slowest because Ollama reloads the model into RAM.

To demo the UI without Ollama, set `app.explainer=stub` in `application.properties`.

### Run with Docker

With [Docker](https://docs.docker.com/get-docker/) installed, nothing else is needed — not even Java:

```
docker compose up --build
```

This builds the app image, starts Ollama, pulls the model into a named volume (first run only, ~1 GB), and serves the app on **http://localhost:8080**. On low-RAM machines a natively installed Ollama is usually faster than the containerized one; the compose file exists mainly so anyone can reproduce the project with one command.

## API

Both endpoints take the same request body:

```json
{
  "code": "IDENTIFICATION DIVISION. ...",
  "language": "auto",
  "mode": "explain"
}
```

- `code` — required, max 60,000 characters.
- `language` — optional; `"auto"`, empty or missing triggers heuristic detection. Recognized: COBOL, Java, C, C++, Fortran, Pascal, PL/SQL, RPG, BASIC, Assembly.
- `mode` — optional; `explain` (default), `summarize`, `bugs`, `refactor` or `diff`. In `diff` mode, language auto-detection looks through the diff markers at the underlying code.

`POST /api/explain` — returns the complete answer as JSON:

```json
{
  "explanation": "This COBOL code calculates the gross pay for an employee...",
  "detectedLanguage": "COBOL",
  "model": "qwen2.5-coder:1.5b",
  "mode": "explain",
  "parts": 1,
  "durationMs": 19133
}
```

`parts` is 1 for a single-call answer, or the number of chunks a large file was split into.

`POST /api/explain/stream` — same request, but the response is a Server-Sent Events stream:

| Event | Payload | Meaning |
|---|---|---|
| `status` | `{"message": "Analyzing part 2 of 5…"}` | progress while chunks of a large file are processed |
| `token` | `{"text": " gross"}` | next fragment of the answer |
| `done` | the full JSON response above | generation finished |
| `error` | `{"error": "message"}` | generation failed |

`GET /api/fetch-source?url=…` — fetches a file from GitHub so it can be explained without pasting:

```json
{ "code": "// SPDX-License-Identifier: ...", "fileName": "fork.c", "truncated": true }
```

Accepts `github.com/{owner}/{repo}/blob/{ref}/{path}` links, `raw.githubusercontent.com` URLs, and gists; anything else is rejected (the endpoint is deliberately not a general-purpose proxy). Files longer than 60,000 characters are truncated.

Errors on the JSON endpoints come back as `{"error": "message"}`: **400** for validation failures and bad URLs, **503** when Ollama is unreachable or the model isn't pulled, **500** for anything unexpected.

## Evaluation harness

How do you know a 1.5b model's explanations are any good — or that a model/prompt change didn't make them worse? The eval profile runs a fixed suite of snippets (COBOL payroll, C binary search, Fortran, pre-generics Java, a bug-hunt case, a bug-fix diff) through the real pipeline and scores each answer against the key points it must mention (with synonyms, case-insensitive):

```
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=eval"
```

No web server is started, so it can run alongside a normal instance. Each case is a real LLM call — expect a few minutes on CPU. Results print as a table and a full report (scores, missed points, and every answer) is written to `eval-report.md`. Cases live in `src/main/resources/eval/` — add a snippet file and an entry in `cases.json` to extend the suite.

## Configuration

All knobs live in `src/main/resources/application.properties`:

| Property | Default | Purpose |
|---|---|---|
| `app.explainer` | `ollama` | LLM backend: `ollama` or `stub` |
| `app.ollama.base-url` | `http://localhost:11434` | Ollama server address |
| `app.ollama.model` | `qwen2.5-coder:1.5b` | Model used for explanations |
| `app.ollama.timeout` | `3m` | Read timeout for slow CPU inference |

**Model choice:** `qwen2.5-coder:1.5b` (~1 GB) is the sweet spot for CPU-only inference on 8 GB RAM — it reliably captures a program's purpose and logic, though fine details can occasionally be imprecise. If accuracy matters more than speed, `qwen2.5-coder:3b` roughly doubles response time and tightens up the details. Pull any new model first: `ollama pull <model>`.

## Project layout

```
src/main/java/com/portfolio/legacyexplainer/
  api/
    ExplainController        POST /api/explain and /api/explain/stream (SSE)
    GlobalExceptionHandler   maps errors to {"error": ...} responses
    dto/                     ExplainRequest / ExplainResponse records
  explain/
    ExplainService           orchestrates detection + chunking + LLM calls
    ExplainMode              per-mode prompts (explain/summarize/bugs/refactor)
    CodeChunker              boundary-aware splitting of large files
    CodeExplainer            interface over the LLM backend
    OllamaCodeExplainer      real implementation (Ollama /api/generate + NDJSON streaming)
    StubCodeExplainer        no-LLM fallback with fake streaming (app.explainer=stub)
    LanguageDetector         regex heuristics per language
  source/
    GitHubSourceFetcher      GitHub URL normalization + fetching (allowlisted hosts)
  eval/
    EvalRunner               eval-profile ApplicationRunner: runs + reports the suite
    EvalScorer               key-point scoring of model answers
    EvalCase                 one suite entry (snippet, mode, expected points)
  config/
    OllamaProperties         app.ollama.* binding
    StreamingConfig          executor for SSE streams
src/main/resources/
  static/index.html          self-contained frontend (no build step)
  eval/                      cases.json + code snippets for the eval suite
```

## Tests

```
.\mvnw.cmd test
```

Covers the REST endpoints (happy path, modes, validation, malformed JSON, SSE streaming, URL rejection), the chunker (boundary-aware splits, oversized lines, no content loss), the service orchestration (token streaming, chunked progress), GitHub URL normalization, the eval scorer, and the language detector. Tests always use the stub explainer, so they run without Ollama.
