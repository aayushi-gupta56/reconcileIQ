package com.reconcileiq.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvestigationReviewController.class)
class InvestigationReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvestigationReviewService reviewService;

    @Test
    void approveReturnsUpdatedReport() throws Exception {
        InvestigationReport report = new InvestigationReport(
                9L,
                java.util.List.of("fact"),
                new ValidatedInvestigation(
                        "summary",
                        java.util.List.of(InvestigationOutputValidator.UNDETERMINED),
                        java.util.List.of("a", "b", "c"),
                        true,
                        ValidationStatus.PASSED,
                        "{}",
                        java.util.List.of()
                ),
                "qwen3:8b",
                "v1",
                12L
        );
        report.approve("ada", "ok");
        when(reviewService.approve(eq(3L), any())).thenReturn(report);

        mockMvc.perform(post("/api/investigations/3/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewer\":\"ada\",\"note\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.observedFacts[0]").value("fact"));
    }
}
