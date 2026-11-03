package com.reconcileiq.ai;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/investigations")
@CrossOrigin(origins = "http://localhost:5173")
public class InvestigationReviewController {

    private final InvestigationReviewService reviewService;

    public InvestigationReviewController(InvestigationReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/{id}/approve")
    public InvestigationReport approve(@PathVariable Long id, @RequestBody ReviewRequest request) {
        return reviewService.approve(id, request);
    }

    @PostMapping("/{id}/dismiss")
    public InvestigationReport dismiss(@PathVariable Long id, @RequestBody ReviewRequest request) {
        return reviewService.dismiss(id, request);
    }

    @PostMapping("/{id}/needs-info")
    public InvestigationReport needsInfo(@PathVariable Long id, @RequestBody ReviewRequest request) {
        return reviewService.needsInfo(id, request);
    }
}
