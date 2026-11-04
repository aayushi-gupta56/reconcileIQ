package com.reconcileiq.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconcileiq.config.ReconcileIqProperties;
import com.reconcileiq.reconciliation.ReconciliationRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends a "reconciliation completed" event to a Zapier Catch Hook.
 *
 * Delivery is strictly best-effort and must never affect the caller:
 *  - nothing happens if no webhook URL is configured;
 *  - the request is sent asynchronously with short timeouts, so it cannot slow the response;
 *  - every failure (bad URL, DNS, timeout, non-2xx, serialisation) is caught and logged at WARN;
 *  - this method never throws.
 */
@Component
public class ZapierNotifier {

    private static final Logger log = LoggerFactory.getLogger(ZapierNotifier.class);

    private final ReconcileIqProperties.Notifications settings;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public ZapierNotifier(ObjectMapper mapper, ReconcileIqProperties properties) {
        this.mapper = mapper;
        this.settings = properties.getNotifications();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public void notifyRunCompleted(ReconciliationRun run) {
        try {
            String webhook = settings.getZapierWebhookUrl();
            if (webhook == null || webhook.isBlank()) {
                log.debug("Zapier webhook not configured; skipping run notification");
                return;
            }

            RunSummary summary = new RunSummary(
                    run.getId(),
                    run.getStartedAt(),
                    run.getTotalOrders(),
                    run.getMatchedCount(),
                    run.getDiscrepancyCount(),
                    run.getUnmatchedPaymentsCount(),
                    reportUrl(run.getId())
            );

            // Flat keys so Zapier can map each one without digging into nested objects.
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", "reconciliation.completed");
            payload.put("run_id", summary.runId());
            payload.put("started_at", summary.startedAt() == null ? null : summary.startedAt().toString());
            payload.put("total_orders", summary.totalOrders());
            payload.put("matched", summary.matched());
            payload.put("discrepancies", summary.discrepancies());
            payload.put("unexpected_payments", summary.unexpectedPayments());
            payload.put("needs_attention", summary.needsAttention());
            payload.put("report_url", summary.reportUrl());
            payload.put("subject", RunEmailRenderer.subject(summary));
            payload.put("headline", RunEmailRenderer.headline(summary));
            payload.put("email_html", RunEmailRenderer.html(summary));
            payload.put("email_text", RunEmailRenderer.text(summary));

            HttpRequest request = HttpRequest.newBuilder(URI.create(webhook.trim()))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();

            String target = hostOf(webhook);
            long runId = summary.runId();
            http.sendAsync(request, BodyHandlers.discarding())
                    .whenComplete((response, error) -> {
                        try {
                            if (error != null) {
                                log.warn("Zapier notification for run #{} failed ({}): {}",
                                        runId, target, error.toString());
                            } else if (response.statusCode() / 100 != 2) {
                                log.warn("Zapier notification for run #{} rejected by {} with HTTP {}",
                                        runId, target, response.statusCode());
                            } else {
                                log.info("Zapier notification for run #{} sent", runId);
                            }
                        } catch (Exception ignored) {
                            // logging must never throw either
                        }
                    });
        } catch (Exception ex) {
            log.warn("Could not send Zapier notification: {}", ex.toString());
        }
    }

    private String reportUrl(Long runId) {
        String base = settings.getDashboardUrl() == null ? "" : settings.getDashboardUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/?view=dashboard&run=" + runId;
    }

    /** Log the host only; the full webhook URL acts like a secret. */
    private static String hostOf(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null ? "webhook" : host;
        } catch (Exception e) {
            return "webhook";
        }
    }
}
