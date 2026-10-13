package com.portfolio.legacyexplainer.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExplainControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void explainsCodeAndDetectsCobol() throws Exception {
        String body = """
                {"code": "IDENTIFICATION DIVISION.\\nPROGRAM-ID. PAYROLL.\\nPROCEDURE DIVISION.\\n    DISPLAY 'HELLO'.", "language": "auto"}
                """;

        mockMvc.perform(post("/api/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.detectedLanguage").value("COBOL"))
                .andExpect(jsonPath("$.mode").value("explain"))
                .andExpect(jsonPath("$.parts").value(1))
                .andExpect(jsonPath("$.model").value(not("")));
    }

    @Test
    void respectsUserProvidedLanguage() throws Exception {
        mockMvc.perform(post("/api/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"DISPLAY 'X'.\", \"language\": \"COBOL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detectedLanguage").value("COBOL"));
    }

    @Test
    void respectsRequestedMode() throws Exception {
        mockMvc.perform(post("/api/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"DISPLAY 'X'.\", \"language\": \"COBOL\", \"mode\": \"bugs\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("bugs"));
    }

    @Test
    void rejectsUnknownMode() throws Exception {
        mockMvc.perform(post("/api/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"DISPLAY 'X'.\", \"mode\": \"poetry\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void rejectsEmptyCode() throws Exception {
        mockMvc.perform(post("/api/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void rejectsNonGitHubFetchUrls() throws Exception {
        mockMvc.perform(get("/api/fetch-source").param("url", "https://example.com/file.c"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void streamsTokensAndFinishesWithDoneEvent() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/explain/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"code\": \"DISPLAY 'X'.\", \"language\": \"COBOL\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        // The emitter writes from another thread; poll until the final event lands.
        String content = "";
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            content = result.getResponse().getContentAsString();
            if (content.contains("\"explanation\"")) break;
            Thread.sleep(50);
        }

        assertTrue(content.contains("\"text\""), "expected token events, got:\n" + content);
        assertTrue(content.contains("\"explanation\""), "expected a done event, got:\n" + content);
        assertTrue(content.contains("\"detectedLanguage\":\"COBOL\""), "unexpected done payload:\n" + content);
    }
}
