package com.reconcileiq.ai;

import com.reconcileiq.reconciliation.ReconciliationResult;
import com.reconcileiq.reconciliation.ReconciliationStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class InvestigationFallbackFactory {

    private final ObservedFactsBuilder factsBuilder;

    public InvestigationFallbackFactory(ObservedFactsBuilder factsBuilder) {
        this.factsBuilder = factsBuilder;
    }

    public ValidatedInvestigation fallback(
            ReconciliationResult result,
            List<InvestigationEvidence> evidence,
            List<String> facts,
            String rawResponse
    ) {
        ReconciliationStatus status = result.getStatus();
        String summary = summary(status, facts);
        return new ValidatedInvestigation(
                summary,
                List.of(InvestigationOutputValidator.UNDETERMINED),
                checks(status, result, evidence),
                true,
                ValidationStatus.FALLBACK,
                rawResponse,
                List.of("deterministic fallback")
        );
    }

    private String summary(ReconciliationStatus status, List<String> facts) {
        String joined = facts == null || facts.isEmpty()
                ? "Observed facts are listed below."
                : String.join("; ", facts);
        String label = status == null ? "discrepancy" : status.name().replace('_', ' ').toLowerCase();
        return "The engine classified this case as " + label + ". " + joined;
    }

    private List<String> checks(
            ReconciliationStatus status,
            ReconciliationResult result,
            List<InvestigationEvidence> evidence
    ) {
        String merchant = factsBuilder.show(result.getMerchantOrderId());
        String paypal = factsBuilder.show(result.getPaypalOrderId());
        List<String> captureIds = factsBuilder.captureIdList(result, evidence);
        String capture = captureIds.isEmpty() ? "the capture" : captureIds.get(0);
        if (status == null) {
            return List.of(
                    "Look up the PayPal order in the PayPal dashboard and read its current status",
                    "Check application and ingestion logs for this merchant reference",
                    "Check webhook delivery logs for related payment events"
            );
        }
        return switch (status) {
            case MISSING_PAYMENT -> List.of(
                    "Look up PayPal order " + paypal + " in the PayPal dashboard and read its status and captures",
                    "Check sync and ingestion job logs for failures around this order's creation time",
                    "Check webhook delivery logs for events on this PayPal order",
                    "Check the merchant system payment state for order " + merchant
            );
            case AMOUNT_MISMATCH -> List.of(
                    "Confirm the order total, line items, shipping and tax in the merchant system at checkout time",
                    "Open capture " + capture + " in PayPal and verify the gross captured amount",
                    "Check whether the order was edited after PayPal order " + paypal + " was created",
                    "Check whether other captures exist on PayPal order " + paypal
            );
            case CURRENCY_MISMATCH -> List.of(
                    "Check the create-order request logs for the currency sent to PayPal for order " + merchant,
                    "Open capture " + capture + " in PayPal and read its currency and any conversion details",
                    "Check the checkout currency settings for this customer's region",
                    "Check the currency field mapping in the ingestion job"
            );
            case DUPLICATE_PAYMENT -> {
                List<String> items = new ArrayList<>();
                items.add("Confirm in the PayPal dashboard that "
                        + (captureIds.isEmpty() ? "each capture" : String.join(" and ", captureIds))
                        + " are separate completed captures");
                items.add("Check application logs for capture requests sent for order " + merchant);
                items.add("Check webhook delivery logs for repeated events on this merchant order");
                items.add("Check whether more than one PayPal order was created for merchant order " + merchant);
                yield items;
            }
            case UNEXPECTED_PAYMENT -> List.of(
                    "Open " + paypal + " in PayPal and read custom_id, invoice_id and description",
                    "Search order ingestion records for that reference and nearby amounts",
                    "Check order ingestion job logs for failures around the capture time",
                    "Check whether another store or integration uses the same PayPal account"
            );
            case REFERENCE_MISMATCH -> List.of(
                    "Read the PayPal order ID stored against merchant order " + merchant + " in the merchant system",
                    "Read the custom_id or invoice_id on PayPal order " + paypal + " in PayPal",
                    "Check the create-order request logs for the reference sent for order " + merchant,
                    "Check whether order " + merchant + " was retried and given a second PayPal order"
            );
            case MATCHED -> List.of(
                    "No investigation checks are required for a matched payment",
                    "Confirm the PayPal dashboard still shows a completed capture",
                    "Keep the human review record for audit"
            );
        };
    }
}
