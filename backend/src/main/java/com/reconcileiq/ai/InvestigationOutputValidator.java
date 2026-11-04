package com.reconcileiq.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconcileiq.reconciliation.ReconciliationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class InvestigationOutputValidator {

    static final String UNDETERMINED =
            "Root cause cannot be determined from the available evidence.";

    private static final Set<String> EXAMPLE_IDS = Set.of(
            "A1", "A2", "A3", "A4", "A6", "P1", "P2", "P3", "P5", "P6",
            "C2", "C3", "C4a", "C4b", "C5"
    );

    private static final Pattern ID_TOKEN = Pattern.compile(
            "\\b(?=[A-Za-z0-9_-]*\\d)[A-Za-z][A-Za-z0-9_-]{1,40}\\b"
    );

    private static final Pattern BANNED_ACTION = Pattern.compile(
            "(?i)\\brefund(?:ing|s)?\\b|\\bvoid(?:ing)?\\b|\\bcapturing\\b"
                    + "|\\bcancel(?:l?ing)?\\b|chang(?:e|ing) (?:the |a |this )?payment"
                    + "|\\bcapture (?:the )?(?:payment|funds|order)\\b"
    );

    private static final Pattern EVIDENCE_ONLY_CHECK = Pattern.compile(
            "(?i)list (?:every |all |the )?capture|compare timestamps|compare amounts"
                    + "|re-read|list every capture id"
    );

    private static final Set<String> IGNORED_TOKENS = Set.of(
            "USD", "EUR", "GBP", "NOT", "PROVIDED", "API", "HTTP", "JSON"
    );

    private final ObjectMapper mapper;

    public InvestigationOutputValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ParseAttempt parseAndValidate(
            String raw,
            ReconciliationResult result,
            List<InvestigationEvidence> evidence
    ) {
        List<String> violations = new ArrayList<>();
        String json = extractJson(raw);
        if (json == null) {
            violations.add("Response is not a single JSON object");
            return ParseAttempt.failed(raw, violations);
        }
        JsonNode node;
        try {
            node = mapper.readTree(json);
        } catch (Exception e) {
            violations.add("Response is not a single JSON object");
            return ParseAttempt.failed(raw, violations);
        }
        if (!node.isObject()) {
            violations.add("Response is not a single JSON object");
            return ParseAttempt.failed(raw, violations);
        }

        String summary = text(node, "summary");
        if (summary == null || summary.isBlank()) {
            violations.add("summary is missing");
        }

        List<String> explanations = stringList(node.get("possibleExplanations"));
        explanations = clean(explanations);
        if (explanations.size() > 2) {
            violations.add("possibleExplanations has more than 2 entries");
        }
        if (explanations.isEmpty()) {
            explanations = List.of(UNDETERMINED);
        } else {
            for (String explanation : explanations) {
                if (!isHedged(explanation)) {
                    violations.add("possibleExplanations must contain may or could");
                    break;
                }
            }
        }

        List<String> checks = clean(stringList(node.get("recommendedChecks")));
        if (checks.size() < 3 || checks.size() > 4) {
            violations.add("recommendedChecks must have 3 or 4 entries");
        }
        for (String check : checks) {
            if (BANNED_ACTION.matcher(check).find()) {
                violations.add("recommendedChecks must not suggest refunding, voiding, capturing, cancelling or changing a payment");
            }
            if (EVIDENCE_ONLY_CHECK.matcher(check).find()) {
                violations.add("recommendedChecks repeats evidence-only actions");
            }
        }

        Set<String> allowed = allowedIds(result, evidence);
        List<String> textFields = new ArrayList<>();
        if (summary != null) {
            textFields.add(summary);
        }
        textFields.addAll(explanations);
        textFields.addAll(checks);
        List<String> invented = inventedIds(String.join(" ", textFields), allowed);
        if (!invented.isEmpty()) {
            violations.add("Invented or example IDs: " + String.join(", ", invented));
        }

        if (!violations.isEmpty()) {
            return ParseAttempt.failed(raw, violations);
        }

        ValidatedInvestigation ok = new ValidatedInvestigation(
                summary.trim(),
                explanations,
                checks,
                true,
                ValidationStatus.PASSED,
                raw,
                List.of()
        );
        return new ParseAttempt(ok, List.of());
    }

    public String correctiveSuffix(List<String> violations) {
        return "\n\nYour previous JSON was rejected for these reasons:\n- "
                + String.join("\n- ", violations)
                + "\nReturn a single JSON object that fixes every violation. "
                + "Do not copy example IDs. Do not recommend refunding, voiding, capturing or cancelling. "
                + "Give 3 or 4 checks that look outside this dataset.";
    }

    public static String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        t = t.replaceAll("(?s)<think>.*?</think>", "").trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return t.substring(start, end + 1);
    }

    public static List<String> inventedIds(String text, Set<String> allowed) {
        Matcher matcher = ID_TOKEN.matcher(text);
        List<String> invented = new ArrayList<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (IGNORED_TOKENS.contains(token.toUpperCase(Locale.ROOT))) {
                continue;
            }
            if (allowed.stream().anyMatch(id -> id.equalsIgnoreCase(token))) {
                continue;
            }
            if (EXAMPLE_IDS.contains(token) || looksLikeId(token)) {
                invented.add(token);
            }
        }
        return invented;
    }

    static boolean looksLikeId(String token) {
        return EXAMPLE_IDS.contains(token)
                || token.contains("-")
                || token.matches("[A-Z]+\\d+[A-Za-z0-9]*")
                || token.matches("[A-Z][0-9][A-Za-z0-9]*");
    }

    public Set<String> allowedIds(ReconciliationResult result, List<InvestigationEvidence> evidence) {
        Set<String> allowed = new LinkedHashSet<>();
        add(allowed, result.getMerchantOrderId());
        add(allowed, result.getPaypalOrderId());
        add(allowed, result.getCaptureId());
        if (evidence != null) {
            for (InvestigationEvidence tx : evidence) {
                add(allowed, tx.captureId());
                add(allowed, tx.paypalOrderId());
                add(allowed, tx.merchantOrderId());
            }
        }
        return allowed;
    }

    public boolean hasBannedPhrase(String text) {
        return text != null && BANNED_ACTION.matcher(text).find();
    }

    public boolean repeatsEvidence(List<String> checks) {
        return checks != null && checks.stream().anyMatch(c -> EVIDENCE_ONLY_CHECK.matcher(c).find());
    }

    private static void add(Set<String> allowed, String value) {
        if (value != null && !value.isBlank()) {
            allowed.add(value.trim());
        }
    }

    private static boolean isHedged(String explanation) {
        if (UNDETERMINED.equalsIgnoreCase(explanation.trim())) {
            return true;
        }
        String lower = explanation.toLowerCase(Locale.ROOT);
        return lower.contains("may") || lower.contains("could");
    }

    private static List<String> clean(List<String> list) {
        return list.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .toList();
    }

    private static List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            if (item.isTextual()) {
                values.add(item.asText());
            }
        }
        return values;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    public record ParseAttempt(ValidatedInvestigation value, List<String> violations) {
        static ParseAttempt failed(String raw, List<String> violations) {
            return new ParseAttempt(
                    new ValidatedInvestigation("", List.of(), List.of(), true,
                            ValidationStatus.FALLBACK, raw, violations),
                    violations
            );
        }

        boolean ok() {
            return violations.isEmpty() && value != null && !value.summary().isBlank();
        }
    }
}
