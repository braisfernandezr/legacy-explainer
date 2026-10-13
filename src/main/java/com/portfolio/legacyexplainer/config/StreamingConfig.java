package com.portfolio.legacyexplainer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Executor for the SSE streaming endpoint: each streamed explanation runs
 * on its own thread while the servlet request stays in async mode.
 */
@Configuration
public class StreamingConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService sseExecutor() {
        return Executors.newCachedThreadPool();
    }
}
