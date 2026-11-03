package com.reconcileiq.ai;

import com.reconcileiq.reconciliation.ReconciliationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class InvestigationPromptBuilder {

    // CHANGED (5): replaced System.out.println with a logger
    private static final Logger log = LoggerFactory.getLogger(InvestigationPromptBuilder.class);

    public record Prompt(String system, String user) {}

    private static final String NOT_PROVIDED = "NOT PROVIDED";

    private static final String SYSTEM_PROMPT = """
            You explain discrepancies flagged by a rule-based payment reconciliation engine.
            The engine has already classified the discrepancy. You do not re-decide it,
            and you do not decide whether a payment succeeded or failed.

            RULES:
            1. State as fact only what appears in the evidence. Nothing else.
            2. A field shown as "NOT PROVIDED" is simply missing data. Never treat it
               as a cause of the discrepancy.
            3. Never claim what PayPal, the customer, or the merchant did. You only
               know what is present or absent in the supplied dataset.
            4. Hypotheses go ONLY in "possibleExplanations", phrased with "may" or
               "could". Use at most 2, and only if they follow directly from the
               evidence. In observedFacts and possibleExplanations, do not mention currency
               conversion, fees, discounts, refunds, partial payments, rounding or
               processing failures unless they appear in the evidence.
               recommendedChecks MAY name these as things to verify in external systems.
            5. If no hypothesis is supported, set possibleExplanations to
               ["Root cause cannot be determined from the available evidence."]
            6. "recommendedChecks" are things an analyst should VERIFY, as short
               imperative sentences. Never recommend changing, refunding, voiding
               or capturing a payment.
            7. Do not output confidence values. requiresHumanReview is always true.
            8. Output a single JSON object. No markdown, no commentary.
            9. Every value in observedFacts must come from the evidence of THIS request.
               The examples show format only. Never copy IDs, amounts or wording from them.
            10. If the evidence shows a capture ID (in "Capture ID(s)" or under
                INDIVIDUAL PAYPAL TRANSACTIONS), include it in observedFacts exactly
                as written.
            11. recommendedChecks must NOT ask the analyst to list, compare or re-read
                anything already shown in the evidence (IDs, amounts, timestamps). Each
                check must point to a source OUTSIDE this dataset: the PayPal dashboard
                or API, application logs, webhook logs, or customer/bank records.
                Give 3 or 4 checks, each specific to this discrepancy.

            OUTPUT SCHEMA:
            {
              "summary": "One sentence stating the discrepancy using only observed facts.",
              "observedFacts": ["fact", "fact"],
              "possibleExplanations": ["hedged hypothesis"],
              "recommendedChecks": ["check", "check"],
              "requiresHumanReview": true
            }
            """;

    private static final String USER_TEMPLATE = """
            RECONCILIATION EVIDENCE
            Status: %s
            Merchant order ID: %s
            PayPal order ID: %s
            Capture ID(s): %s
            Expected amount: %s
            Actual amount: %s
            Amount difference (actual - expected): %s
            Expected currency: %s
            Actual currency: %s
            Matched transaction count: %s
            Engine explanation: %s

            COMPUTED OBSERVATIONS (calculated by the system, copy them, do not recompute)
            %s

            STATUS GUIDANCE
            %s

            INDIVIDUAL PAYPAL TRANSACTIONS
            %s

            Return the JSON object now.
            """;

    // CHANGED (2): "NOT PROVIDED" removed from every example's observedFacts,
    // DUPLICATE_PAYMENT now lists capture IDs, and guidance tells the model to copy
    // IDs from the evidence instead of treating a missing capture ID as expected.
    private static final Map<String, String> GUIDANCE = Map.of(

            "MISSING_PAYMENT", """
                    State that no matching payment record was found in the supplied dataset
                    for this merchant order. This does NOT prove the customer did not pay
                    or that PayPal has no transaction.
                    Do NOT tell the analyst to confirm the match count or re-read the order
                    fields, the evidence already shows them. Checks must go beyond the dataset:
                    - look up the PayPal order ID in the PayPal dashboard or Orders API and
                      read its status and any capture on it (if the PayPal order ID is
                      NOT PROVIDED, search PayPal by the merchant reference instead)
                    - check transaction sync and ingestion job logs for failures in this
                      order's time window
                    - check that the dataset's date range and account filter include this
                      order's creation date
                    - check the merchant system for this order's payment state and any
                      checkout error or abandoned-checkout record
                    - check webhook delivery logs for events on this PayPal order
                    Example output (format only, do not copy values):
                    {"summary":"No payment record matching merchant order A1 was found in the supplied dataset.",
                    "observedFacts":["Merchant order A1 has PayPal order ID P1","Matched transaction count is 0"],
                    "possibleExplanations":["The transaction may not have been ingested or synchronised into the dataset."],
                    "recommendedChecks":["Look up PayPal order P1 in the PayPal dashboard and read its status and captures","Check sync and ingestion job logs for failures around this order's creation time","Check that the dataset's date range and account filter include this order's date","Check webhook delivery logs for events on PayPal order P1"],
                    "requiresHumanReview":true}
                    """,

            "AMOUNT_MISMATCH", """
                    State both amounts and the supplied difference. Do not guess why they differ.
                    Include the capture ID in observedFacts if one is shown.
                    Do NOT tell the analyst to compare the two amounts, the evidence already
                    shows them. Checks must go beyond the dataset:
                    - confirm the order total in the merchant system at checkout time,
                      including line items, shipping and tax, and whether the order was
                      edited after the PayPal order was created
                    - open the capture in PayPal and verify the gross captured amount, and
                      verify the dataset's amount field is the gross figure and not net
                    - check the PayPal order's original purchase amount against the
                      merchant order total
                    - check whether other captures, partial captures or refunds exist on
                      the PayPal order
                    Example output (format only, do not copy values):
                    {"summary":"Order A2 expected 100.00 USD but the matched capture shows 90.00 USD, a difference of -10.00.",
                    "observedFacts":["Expected amount is 100.00 USD","Actual amount is 90.00 USD","Difference is -10.00","Currencies match","Capture ID is C2"],
                    "possibleExplanations":["Root cause cannot be determined from the available evidence."],
                    "recommendedChecks":["Confirm the order total, line items, shipping and tax in the merchant system at checkout time","Open capture C2 in PayPal and verify the gross captured amount","Check whether the order was edited after PayPal order P2 was created","Check whether other captures or refunds exist on PayPal order P2"],
                    "requiresHumanReview":true}
                    """,

            "CURRENCY_MISMATCH", """
                    State both currencies. Amounts being numerically equal does not mean the
                    payment is correct. Do not assume conversion occurred.
                    Include the capture ID in observedFacts if one is shown.
                    Do NOT tell the analyst to compare the two currencies, the evidence
                    already shows them. Checks must go beyond the dataset:
                    - check the create-order request or logs to see which currency the
                      merchant sent to PayPal for this order
                    - open the capture in PayPal and read its currency and whether the
                      record shows any currency conversion
                    - check the merchant's checkout currency settings for this customer's
                      region or store
                    - check the ingestion mapping of the currency field for this provider
                    Example output (format only, do not copy values):
                    {"summary":"Order A3 expected currency USD but the matched capture shows EUR.",
                    "observedFacts":["Expected currency is USD","Actual currency is EUR","Both amounts are 50.00","Capture ID is C3"],
                    "possibleExplanations":["Root cause cannot be determined from the available evidence."],
                    "recommendedChecks":["Check the create-order request logs for the currency sent to PayPal for order A3","Open capture C3 in PayPal and read its currency and any conversion details","Check the checkout currency settings for this customer's region","Check the currency field mapping in the ingestion job"],
                    "requiresHumanReview":true}
                    """,

            "DUPLICATE_PAYMENT", """
                    State the matched transaction count. List EVERY capture ID shown under
                    INDIVIDUAL PAYPAL TRANSACTIONS in observedFacts, copied exactly, and
                    include the amount and timing facts from COMPUTED OBSERVATIONS.
                    A possible explanation is allowed only if it follows from those facts
                    (for example identical amounts captured close together).
                    Do NOT tell the analyst to list or compare captures, amounts or
                    timestamps, the evidence already shows them. Checks must go beyond
                    the dataset:
                    - confirm in the PayPal dashboard that each capture is a separate,
                      completed capture on the account
                    - check application and API logs for how many capture requests were sent
                      for this order and whether the same PayPal-Request-Id was reused
                    - check webhook delivery logs for repeated events for the same capture
                    - check whether more than one PayPal order was created for this
                      merchant order
                    - check the customer's payment records or support tickets for two charges
                    Never recommend automatic refunds.
                    Example output (format only, do not copy values):
                    {"summary":"2 transactions in the dataset matched merchant order A4.",
                    "observedFacts":["Matched transaction count is 2","Capture IDs are C4a and C4b","Both transactions are 25.00 USD","Captures are 60 seconds apart"],
                    "possibleExplanations":["Identical amounts captured 60 seconds apart may indicate a retried or double-submitted capture request."],
                    "recommendedChecks":["Confirm in the PayPal dashboard that C4a and C4b are both separate captures on the account","Check application logs for the number of capture requests sent for order A4 and whether the same PayPal-Request-Id was used","Check webhook delivery logs for repeated events for C4a or C4b","Check whether more than one PayPal order was created for merchant order A4"],
                    "requiresHumanReview":true}
                    """,

            "UNEXPECTED_PAYMENT", """
                    State that a provider transaction exists with no corresponding merchant order
                    in the supplied dataset.
                    Do NOT tell the analyst to confirm the merchant order is absent, the
                    evidence already shows it. Checks must go beyond the dataset:
                    - open the PayPal order and capture and read the merchant reference on
                      them (custom_id, invoice_id, description)
                    - search the merchant order system and order ingestion records for that
                      reference, and for orders with the same amount around the capture time
                    - check order ingestion job logs for failures around the capture time
                    - check whether another store, channel or integration uses the same
                      PayPal account
                    - check the payer details on the capture against customer support
                      tickets or customer records
                    Example output (format only, do not copy values):
                    {"summary":"PayPal order P5 has no corresponding merchant order in the supplied dataset.",
                    "observedFacts":["PayPal order ID is P5","Capture ID is C5","No merchant order ID is present in the dataset"],
                    "possibleExplanations":["The merchant order may be missing from order ingestion."],
                    "recommendedChecks":["Open P5 and C5 in PayPal and read custom_id, invoice_id and description","Search order ingestion records for that reference and for orders of the same amount near the capture time","Check order ingestion job logs for failures around the capture time","Check whether another store or integration uses the same PayPal account"],
                    "requiresHumanReview":true}
                    """,

            "REFERENCE_MISMATCH", """
                    State the conflicting identifiers exactly as supplied.
                    Do NOT tell the analyst to compare the identifiers shown in the evidence.
                    Checks must go beyond the dataset:
                    - in the merchant system, read the PayPal order ID stored against this
                      merchant order
                    - in PayPal, read the custom_id or invoice_id on the PayPal order and
                      see which merchant order it points to
                    - check the create-order request logs for the reference sent to PayPal
                    - check whether the merchant order was retried and given a second
                      PayPal order
                    - check the field mapping in the ingestion job for both identifiers
                    Example output (format only, do not copy values):
                    {"summary":"The merchant order and PayPal order identifiers conflict.",
                    "observedFacts":["Merchant order ID is A6","PayPal order ID is P6"],
                    "possibleExplanations":["Root cause cannot be determined from the available evidence."],
                    "recommendedChecks":["Read the PayPal order ID stored against merchant order A6 in the merchant system","Read the custom_id or invoice_id on PayPal order P6 in PayPal","Check the create-order request logs for the reference sent for order A6","Check whether order A6 was retried and given a second PayPal order"],
                    "requiresHumanReview":true}
                    """
    );

    public Prompt build(ReconciliationResult result, List<InvestigationEvidence> evidence) {
        String status = result.getStatus() == null ? "" : result.getStatus().name();

        String user = USER_TEMPLATE.formatted(
                show(status),
                show(result.getMerchantOrderId()),
                show(result.getPaypalOrderId()),
                captureIds(result, evidence),   // CHANGED (1): was show(result.getCaptureId())
                show(result.getExpectedAmount()),
                show(result.getActualAmount()),
                difference(result),
                show(result.getExpectedCurrency()),
                show(result.getActualCurrency()),
                show(result.getMatchedTransactionCount()),
                show(result.getExplanation()),
                derivedObservations(evidence),   // CHANGED (6): computed in Java
                GUIDANCE.getOrDefault(status, "No specific guidance. Describe only observed facts."),
                formatEvidence(evidence)
        );

        // CHANGED (5): debug output via logger instead of System.out
        if (log.isDebugEnabled()) {
            log.debug("LLM user prompt:\n{}", user);
            log.debug("LLM system prompt:\n{}", SYSTEM_PROMPT);
        }

        return new Prompt(SYSTEM_PROMPT, user);
    }

    private static String show(Object value) {
        if (value == null) return NOT_PROVIDED;
        String s = value.toString();
        return s.isBlank() || "null".equalsIgnoreCase(s) ? NOT_PROVIDED : s;
    }

    // CHANGED (1): NEW helper. Uses the result's capture ID if present; otherwise
    // (e.g. duplicates, where several transactions matched) collects every capture ID
    // from the evidence list so the model sees real IDs instead of NOT PROVIDED.
    private static String captureIds(ReconciliationResult result, List<InvestigationEvidence> evidence) {
        String direct = show(result.getCaptureId());
        if (!NOT_PROVIDED.equals(direct)) return direct;

        if (evidence == null || evidence.isEmpty()) return NOT_PROVIDED;

        String ids = evidence.stream()
                .map(tx -> show(tx.captureId()))
                .filter(id -> !NOT_PROVIDED.equals(id))
                .distinct()
                .collect(Collectors.joining(", "));

        return ids.isEmpty() ? NOT_PROVIDED : ids;
    }

    // CHANGED (6): NEW. Deterministic facts computed in Java so the model does not
    // have to compare amounts or do time arithmetic, and can ground hypotheses in them.
    private static String derivedObservations(List<InvestigationEvidence> evidence) {
        if (evidence == null || evidence.size() < 2) return "None.";

        List<String> lines = new ArrayList<>();

        long amounts = evidence.stream().map(tx -> show(tx.amount())).distinct().count();
        lines.add(amounts == 1
                ? "All transactions have the same amount: " + show(evidence.get(0).amount()) + "."
                : "The transactions have different amounts.");

        long currencies = evidence.stream().map(tx -> show(tx.currency())).distinct().count();
        lines.add(currencies == 1
                ? "All transactions have the same currency: " + show(evidence.get(0).currency()) + "."
                : "The transactions have different currencies.");

        List<LocalDateTime> times = evidence.stream()
                .map(tx -> parseTime(tx.capturedAt()))
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        if (times.size() >= 2) {
            long seconds = Duration.between(times.get(0), times.get(times.size() - 1)).getSeconds();
            lines.add("Time between the first and last capture: " + seconds + " seconds.");
        }

        return String.join("\n", lines);
    }

    private static LocalDateTime parseTime(Object value) {
        if (value == null) return null;
        String s = value.toString();
        try {
            return LocalDateTime.parse(s);
        } catch (Exception e) {
            try {
                return OffsetDateTime.parse(s).toLocalDateTime();
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private String formatEvidence(List<InvestigationEvidence> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return "No associated PayPal transactions found.";
        }

        return IntStream.range(0, evidence.size())
                .mapToObj(i -> {
                    InvestigationEvidence tx = evidence.get(i);

                    return """
                            Transaction %d:
                              Capture ID: %s
                              PayPal Order ID: %s
                              Merchant Order ID: %s
                              Amount: %s
                              Currency: %s
                              Status: %s
                              Captured At: %s
                            """.formatted(
                            i + 1,
                            show(tx.captureId()),
                            show(tx.paypalOrderId()),
                            show(tx.merchantOrderId()),
                            show(tx.amount()),
                            show(tx.currency()),
                            show(tx.status()),
                            show(tx.capturedAt())
                    );
                })
                .collect(Collectors.joining("\n"));
    }

    // Computed in Java: small models are unreliable at arithmetic.
    private static String difference(ReconciliationResult result) {
        BigDecimal expected = result.getExpectedAmount();
        BigDecimal actual = result.getActualAmount();
        if (expected == null || actual == null) return "NOT APPLICABLE";
        return actual.subtract(expected).toPlainString();
    }
}