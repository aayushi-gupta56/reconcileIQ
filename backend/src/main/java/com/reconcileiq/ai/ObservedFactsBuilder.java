package com.reconcileiq.ai;

import com.reconcileiq.reconciliation.ReconciliationResult;
import com.reconcileiq.reconciliation.ReconciliationStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Deterministic facts for an investigation. The model must not invent these.
 */
@Component
public class ObservedFactsBuilder {

    public static final String NOT_PROVIDED = "NOT PROVIDED";

    public List<String> build(ReconciliationResult result, List<InvestigationEvidence> evidence) {
        List<InvestigationEvidence> txs = evidence == null ? List.of() : evidence;
        ReconciliationStatus status = result.getStatus();
        if (status == null) {
            return List.of();
        }
        return switch (status) {
            case DUPLICATE_PAYMENT -> duplicateFacts(result, txs);
            case AMOUNT_MISMATCH -> amountFacts(result, txs);
            case CURRENCY_MISMATCH -> currencyFacts(result, txs);
            case MISSING_PAYMENT -> missingFacts(result);
            case UNEXPECTED_PAYMENT -> unexpectedFacts(result, txs);
            case REFERENCE_MISMATCH -> referenceFacts(result, txs);
            case MATCHED -> matchedFacts(result, txs);
        };
    }

    public String captureIds(ReconciliationResult result, List<InvestigationEvidence> evidence) {
        List<String> ids = captureIdList(result, evidence);
        return ids.isEmpty() ? NOT_PROVIDED : String.join(", ", ids);
    }

    public List<String> captureIdList(ReconciliationResult result, List<InvestigationEvidence> evidence) {
        List<String> ids = new ArrayList<>();
        if (result.getCaptureId() != null && !result.getCaptureId().isBlank()) {
            ids.add(result.getCaptureId().trim());
        }
        if (evidence != null) {
            for (InvestigationEvidence tx : evidence) {
                String id = tx.captureId();
                if (id != null && !id.isBlank() && !ids.contains(id)) {
                    ids.add(id.trim());
                }
            }
        }
        return ids;
    }

    public String derivedObservations(List<InvestigationEvidence> evidence) {
        if (evidence == null || evidence.size() < 2) {
            return "None.";
        }
        List<String> lines = new ArrayList<>();

        long amounts = evidence.stream().map(tx -> show(tx.amount())).distinct().count();
        lines.add(amounts == 1
                ? "All transactions have the same amount: " + show(evidence.get(0).amount()) + "."
                : "The transactions have different amounts.");

        long currencies = evidence.stream().map(tx -> show(tx.currency())).distinct().count();
        lines.add(currencies == 1
                ? "All transactions have the same currency: " + show(evidence.get(0).currency()) + "."
                : "The transactions have different currencies.");

        long seconds = captureGapSeconds(evidence);
        if (seconds >= 0) {
            lines.add("Time between the first and last capture: " + seconds + " seconds.");
        }
        return String.join("\n", lines);
    }

    public String difference(ReconciliationResult result) {
        BigDecimal expected = result.getExpectedAmount();
        BigDecimal actual = result.getActualAmount();
        if (expected == null || actual == null) {
            return "NOT APPLICABLE";
        }
        return actual.subtract(expected).toPlainString();
    }

    public String show(Object value) {
        if (value == null) {
            return NOT_PROVIDED;
        }
        String s = value instanceof BigDecimal decimal
                ? decimal.setScale(2, RoundingMode.HALF_UP).toPlainString()
                : value.toString();
        return s.isBlank() || "null".equalsIgnoreCase(s) ? NOT_PROVIDED : s;
    }

    private List<String> duplicateFacts(ReconciliationResult result, List<InvestigationEvidence> evidence) {
        List<String> facts = new ArrayList<>();
        facts.add("Matched transaction count is " + result.getMatchedTransactionCount());
        List<String> ids = captureIdList(result, evidence);
        if (!ids.isEmpty()) {
            facts.add(ids.size() == 1
                    ? "Capture ID is " + ids.get(0)
                    : "Capture IDs are " + joinAnd(ids));
        }
        if (!evidence.isEmpty()) {
            long amountCount = evidence.stream().map(tx -> money(tx.amount(), tx.currency())).distinct().count();
            if (amountCount == 1) {
                String label = evidence.size() == 2 ? "Both transactions are " : "All transactions are ";
                facts.add(label + money(evidence.get(0).amount(), evidence.get(0).currency()));
            } else {
                facts.add("The transactions have different amounts.");
            }
            long seconds = captureGapSeconds(evidence);
            if (seconds >= 0) {
                facts.add("Captures are " + seconds + " seconds apart");
            }
        }
        return facts;
    }

    private List<String> amountFacts(ReconciliationResult result, List<InvestigationEvidence> evidence) {
        List<String> facts = new ArrayList<>();
        facts.add("Expected amount is " + money(result.getExpectedAmount(), result.getExpectedCurrency()));
        facts.add("Actual amount is " + money(result.getActualAmount(), result.getActualCurrency()));
        facts.add("Difference is " + difference(result));
        facts.add(currenciesMatch(result) ? "Currencies match" : "Currencies differ");
        List<String> ids = captureIdList(result, evidence);
        if (!ids.isEmpty()) {
            facts.add("Capture ID is " + ids.get(0));
        }
        return facts;
    }

    private List<String> currencyFacts(ReconciliationResult result, List<InvestigationEvidence> evidence) {
        List<String> facts = new ArrayList<>();
        facts.add("Expected currency is " + show(result.getExpectedCurrency()));
        facts.add("Actual currency is " + show(result.getActualCurrency()));
        if (result.getExpectedAmount() != null && result.getActualAmount() != null
                && result.getExpectedAmount().compareTo(result.getActualAmount()) == 0) {
            facts.add("Both amounts are " + show(result.getExpectedAmount()));
        } else {
            facts.add("Expected amount is " + show(result.getExpectedAmount())
                    + " and actual amount is " + show(result.getActualAmount()));
        }
        List<String> ids = captureIdList(result, evidence);
        if (!ids.isEmpty()) {
            facts.add("Capture ID is " + ids.get(0));
        }
        return facts;
    }

    private List<String> missingFacts(ReconciliationResult result) {
        List<String> facts = new ArrayList<>();
        facts.add("Merchant order " + show(result.getMerchantOrderId())
                + " has PayPal order ID " + show(result.getPaypalOrderId()));
        facts.add("Matched transaction count is " + result.getMatchedTransactionCount());
        return facts;
    }

    private List<String> unexpectedFacts(ReconciliationResult result, List<InvestigationEvidence> evidence) {
        List<String> facts = new ArrayList<>();
        facts.add("PayPal order ID is " + show(result.getPaypalOrderId()));
        List<String> ids = captureIdList(result, evidence);
        if (!ids.isEmpty()) {
            facts.add("Capture ID is " + ids.get(0));
        }
        String merchant = firstNonBlank(result.getMerchantOrderId(),
                evidence.stream().map(InvestigationEvidence::merchantOrderId).filter(Objects::nonNull).findFirst().orElse(null));
        if (merchant == null || merchant.isBlank()) {
            facts.add("No merchant order ID is present in the dataset");
        } else {
            facts.add("Merchant order ID in the payment record is " + merchant);
        }
        return facts;
    }

    private List<String> referenceFacts(ReconciliationResult result, List<InvestigationEvidence> evidence) {
        List<String> facts = new ArrayList<>();
        facts.add("Merchant order ID is " + show(result.getMerchantOrderId()));
        facts.add("PayPal order ID is " + show(result.getPaypalOrderId()));
        evidence.stream()
                .map(InvestigationEvidence::merchantOrderId)
                .filter(id -> id != null && !id.isBlank()
                        && !id.equals(result.getMerchantOrderId()))
                .distinct()
                .forEach(id -> facts.add("Payment merchant reference is " + id));
        List<String> ids = captureIdList(result, evidence);
        if (!ids.isEmpty()) {
            facts.add("Capture ID is " + ids.get(0));
        }
        return facts;
    }

    private List<String> matchedFacts(ReconciliationResult result, List<InvestigationEvidence> evidence) {
        List<String> facts = new ArrayList<>();
        facts.add("Merchant order ID is " + show(result.getMerchantOrderId()));
        facts.add("PayPal order ID is " + show(result.getPaypalOrderId()));
        facts.add("Expected amount is " + money(result.getExpectedAmount(), result.getExpectedCurrency()));
        facts.add("Actual amount is " + money(result.getActualAmount(), result.getActualCurrency()));
        List<String> ids = captureIdList(result, evidence);
        if (!ids.isEmpty()) {
            facts.add("Capture ID is " + ids.get(0));
        }
        return facts;
    }

    private long captureGapSeconds(List<InvestigationEvidence> evidence) {
        List<LocalDateTime> times = evidence.stream()
                .map(tx -> parseTime(tx.capturedAt()))
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        if (times.size() < 2) {
            return -1;
        }
        return Duration.between(times.get(0), times.get(times.size() - 1)).getSeconds();
    }

    static LocalDateTime parseTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
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

    private static boolean currenciesMatch(ReconciliationResult result) {
        return result.getExpectedCurrency() != null
                && result.getActualCurrency() != null
                && result.getExpectedCurrency().equalsIgnoreCase(result.getActualCurrency());
    }

    private String money(BigDecimal amount, String currency) {
        if (amount == null) {
            return NOT_PROVIDED;
        }
        String n = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        return currency == null || currency.isBlank() ? n : n + " " + currency;
    }

    private static String joinAnd(List<String> ids) {
        if (ids.size() == 1) {
            return ids.get(0);
        }
        if (ids.size() == 2) {
            return ids.get(0) + " and " + ids.get(1);
        }
        return ids.stream().limit(ids.size() - 1).collect(Collectors.joining(", "))
                + " and " + ids.get(ids.size() - 1);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
