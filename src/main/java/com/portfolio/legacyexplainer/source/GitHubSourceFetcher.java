package com.portfolio.legacyexplainer.source;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Fetches source code from GitHub so users can point the explainer at a
 * file instead of pasting it. Only GitHub hosts are accepted (never
 * arbitrary URLs — this endpoint must not become a generic proxy), and
 * regular github.com / gist.github.com links are rewritten to their raw
 * content equivalents.
 */
@Component
public class GitHubSourceFetcher {

    /** Matches the request DTO's max code size; longer files are truncated. */
    static final int MAX_CHARS = 60_000;

    private final RestClient restClient;

    public GitHubSourceFetcher() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build());
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public FetchedSource fetch(String url) {
        URI rawUri = toRawUri(url);

        String body;
        try {
            body = restClient.get().uri(rawUri).retrieve().body(String.class);
        } catch (RestClientResponseException ex) {
            throw new SourceFetchException(
                    "GitHub returned HTTP " + ex.getStatusCode().value()
                            + " — check that the file exists and the repository is public");
        } catch (RestClientException ex) {
            throw new SourceFetchException("Could not fetch the file: " + ex.getMessage());
        }

        if (body == null || body.isBlank()) {
            throw new SourceFetchException("The URL returned no content");
        }
        if (body.indexOf('\0') >= 0) {
            throw new SourceFetchException("The URL points to a binary file, not source code");
        }

        boolean truncated = body.length() > MAX_CHARS;
        return new FetchedSource(
                truncated ? body.substring(0, MAX_CHARS) : body,
                fileName(rawUri),
                truncated);
    }

    /**
     * Accepts and normalizes:
     * <ul>
     *   <li>{@code raw.githubusercontent.com/...} and {@code gist.githubusercontent.com/...} — used as-is</li>
     *   <li>{@code github.com/{owner}/{repo}/blob|raw/{ref}/{path}} — rewritten to raw.githubusercontent.com</li>
     *   <li>{@code gist.github.com/{user}/{id}} — rewritten to the gist's raw content</li>
     * </ul>
     */
    static URI toRawUri(String url) {
        URI uri;
        try {
            uri = new URI(url == null ? "" : url.trim());
        } catch (URISyntaxException ex) {
            throw new SourceFetchException("That is not a valid URL");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        String path = uri.getPath() == null ? "" : uri.getPath();

        switch (host) {
            case "raw.githubusercontent.com", "gist.githubusercontent.com" -> {
                return https(host, path);
            }
            case "github.com", "www.github.com" -> {
                // /{owner}/{repo}/blob/{ref}/{path...} → /{owner}/{repo}/{ref}/{path...}
                String[] parts = path.split("/", 6);
                if (parts.length == 6 && ("blob".equals(parts[3]) || "raw".equals(parts[3]))) {
                    return https("raw.githubusercontent.com",
                            "/" + parts[1] + "/" + parts[2] + "/" + parts[4] + "/" + parts[5]);
                }
                throw new SourceFetchException(
                        "Use a link to a file (github.com/owner/repo/blob/branch/path), not to a repository");
            }
            case "gist.github.com" -> {
                String[] parts = path.split("/", 4);
                if (parts.length >= 3 && !parts[2].isBlank()) {
                    return https("gist.githubusercontent.com", "/" + parts[1] + "/" + parts[2] + "/raw");
                }
                throw new SourceFetchException("Use a link to a gist (gist.github.com/user/id)");
            }
            default -> throw new SourceFetchException(
                    "Only GitHub URLs are supported: github.com file links, raw.githubusercontent.com or gists");
        }
    }

    private static URI https(String host, String path) {
        try {
            return new URI("https", host, path, null);
        } catch (URISyntaxException ex) {
            throw new SourceFetchException("That is not a valid URL");
        }
    }

    private static String fileName(URI rawUri) {
        String path = rawUri.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.isBlank() ? "file" : name;
    }
}
