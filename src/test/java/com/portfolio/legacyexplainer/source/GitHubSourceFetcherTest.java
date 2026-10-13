package com.portfolio.legacyexplainer.source;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitHubSourceFetcherTest {

    @Test
    void rewritesBlobUrlToRawUrl() {
        assertEquals("https://raw.githubusercontent.com/apache/tomcat/main/build.xml",
                GitHubSourceFetcher.toRawUri(
                        "https://github.com/apache/tomcat/blob/main/build.xml").toString());
    }

    @Test
    void rewritesBlobUrlWithNestedPath() {
        assertEquals("https://raw.githubusercontent.com/owner/repo/v1.2/src/main/legacy/prog.cbl",
                GitHubSourceFetcher.toRawUri(
                        "https://github.com/owner/repo/blob/v1.2/src/main/legacy/prog.cbl").toString());
    }

    @Test
    void keepsRawUrlAsIs() {
        assertEquals("https://raw.githubusercontent.com/owner/repo/main/file.c",
                GitHubSourceFetcher.toRawUri(
                        "https://raw.githubusercontent.com/owner/repo/main/file.c").toString());
    }

    @Test
    void rewritesGistPageToRawContent() {
        assertEquals("https://gist.githubusercontent.com/someuser/abc123/raw",
                GitHubSourceFetcher.toRawUri(
                        "https://gist.github.com/someuser/abc123").toString());
    }

    @Test
    void rejectsNonGitHubHosts() {
        SourceFetchException ex = assertThrows(SourceFetchException.class,
                () -> GitHubSourceFetcher.toRawUri("https://gitlab.com/owner/repo/-/blob/main/file.c"));
        assertEquals(true, ex.getMessage().contains("GitHub"));
    }

    @Test
    void rejectsRepositoryRootLinks() {
        assertThrows(SourceFetchException.class,
                () -> GitHubSourceFetcher.toRawUri("https://github.com/apache/tomcat"));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(SourceFetchException.class,
                () -> GitHubSourceFetcher.toRawUri("not a url at all"));
    }
}
