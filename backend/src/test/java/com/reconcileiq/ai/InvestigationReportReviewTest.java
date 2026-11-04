package com.reconcileiq.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvestigationReportReviewTest {

    @Test
    void approveAndDismissRequirePendingStatus() {
        InvestigationReport report = newReport();
        report.approve("analyst@example.com", "Looks right");
        assertThat(report.getStatus()).isEqualTo(InvestigationReport.APPROVED);
        assertThat(report.getReviewedBy()).isEqualTo("analyst@example.com");
        assertThat(report.getReviewedAt()).isNotNull();

        assertThatThrownBy(() -> report.dismiss("other", "no"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("APPROVED");
    }

    @Test
    void cannotApproveDismissed() {
        InvestigationReport report = newReport();
        report.dismiss("analyst@example.com", "noise");
        assertThatThrownBy(() -> report.approve("analyst@example.com", "changed mind"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DISMISSED");
    }

    @Test
    void reviewerIsRequired() {
        InvestigationReport report = newReport();
        assertThatThrownBy(() -> report.approve("  ", "note"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static InvestigationReport newReport() {
        ValidatedInvestigation investigation = new ValidatedInvestigation(
                "summary",
                List.of(InvestigationOutputValidator.UNDETERMINED),
                List.of("a", "b", "c"),
                true,
                ValidationStatus.PASSED,
                "{}",
                List.of()
        );
        return new InvestigationReport(1L, List.of("fact"), investigation, "qwen3:8b", "v1", 10L);
    }
}
