package com.portfolio.legacyexplainer.api;

import com.portfolio.legacyexplainer.api.dto.ExplainRequest;
import com.portfolio.legacyexplainer.api.dto.ExplainResponse;
import com.portfolio.legacyexplainer.explain.ExplainService;
import com.portfolio.legacyexplainer.explain.ExplainerUnavailableException;
import com.portfolio.legacyexplainer.source.FetchedSource;
import com.portfolio.legacyexplainer.source.GitHubSourceFetcher;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/api")
public class ExplainController {

    /**
     * A chunked run on CPU can take many minutes (one LLM call per part),
     * so the SSE connection gets a much longer timeout than regular requests.
     */
    private static final long STREAM_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private static final Logger log = LoggerFactory.getLogger(ExplainController.class);

    private final ExplainService explainService;
    private final GitHubSourceFetcher sourceFetcher;
    private final ExecutorService sseExecutor;

    public ExplainController(ExplainService explainService, GitHubSourceFetcher sourceFetcher,
                             ExecutorService sseExecutor) {
        this.explainService = explainService;
        this.sourceFetcher = sourceFetcher;
        this.sseExecutor = sseExecutor;
    }

    @PostMapping("/explain")
    public ExplainResponse explain(@Valid @RequestBody ExplainRequest request) {
        return explainService.explain(request);
    }

    /** Fetches a source file from GitHub so the UI can fill the editor from a URL. */
    @GetMapping("/fetch-source")
    public FetchedSource fetchSource(@RequestParam String url) {
        return sourceFetcher.fetch(url);
    }

    /**
     * Streaming variant: emits {@code status} events while parts of a large
     * file are analyzed, {@code token} events as the answer is generated,
     * then one {@code done} event with the full {@link ExplainResponse}.
     * Failures arrive as an {@code error} event.
     */
    @PostMapping(value = "/explain/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter explainStream(@Valid @RequestBody ExplainRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        sseExecutor.execute(() -> stream(request, emitter));
        return emitter;
    }

    private void stream(ExplainRequest request, SseEmitter emitter) {
        try {
            ExplainResponse response = explainService.explain(request, new ExplainService.StreamListener() {
                @Override
                public void onStatus(String message) {
                    send(emitter, "status", Map.of("message", message));
                }

                @Override
                public void onToken(String token) {
                    send(emitter, "token", Map.of("text", token));
                }
            });
            send(emitter, "done", response);
            emitter.complete();
        } catch (ClientDisconnectedException ex) {
            log.debug("Client disconnected mid-stream, generation aborted");
            emitter.complete();
        } catch (ExplainerUnavailableException ex) {
            log.warn("LLM backend unavailable during stream: {}", ex.getMessage());
            finishWithError(emitter, ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error during streamed explanation", ex);
            finishWithError(emitter, "Something went wrong while explaining the code. Please try again.");
        }
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception ex) {
            // Sending failed → the client is gone; abort the LLM generation.
            throw new ClientDisconnectedException(ex);
        }
    }

    private void finishWithError(SseEmitter emitter, String message) {
        try {
            send(emitter, "error", Map.of("error", message));
        } catch (ClientDisconnectedException ignored) {
            // Nothing left to inform.
        }
        emitter.complete();
    }

    private static final class ClientDisconnectedException extends RuntimeException {
        ClientDisconnectedException(Throwable cause) {
            super(cause);
        }
    }
}
