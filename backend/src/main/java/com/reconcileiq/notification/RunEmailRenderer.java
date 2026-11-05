package com.reconcileiq.notification;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Builds the subject, HTML and plain-text bodies of the "reconciliation completed" email.
 *
 * Pure JDK on purpose (no Spring, no templating library) so it is trivial to test and cannot
 * fail at startup. The HTML is table-based with inline styles, which is what Gmail and
 * Outlook render reliably.
 */
public final class RunEmailRenderer {

    // PayPal palette, shared with the dashboard
    private static final String NAVY = "#003087";
    private static final String BLUE = "#0070E0";
    private static final String GOLD = "#FFC439";
    private static final String INK = "#2C2E2F";
    private static final String SLATE = "#687173";
    private static final String LINE = "#E6EAEE";
    private static final String SURFACE = "#F5F7FA";
    private static final String SUCCESS = "#0B7A43";
    private static final String DANGER = "#C72E2E";
    private static final String WARNING = "#9A5B00";

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.ENGLISH);

    private RunEmailRenderer() {
    }

    public static String subject(RunSummary run) {
        return "Reconciliation completed: Run #" + run.runId();
    }

    public static String headline(RunSummary run) {
        return run.needsAttention() ? "Discrepancies found" : "All payments matched";
    }

    public static String text(RunSummary run) {
        return "ReconcileIQ - " + headline(run) + "\n\n"
                + "A reconciliation run has been completed.\n"
                + whenLine(run) + "\n\n"
                + "Total orders:        " + run.totalOrders() + "\n"
                + "Matched:             " + run.matched() + "\n"
                + "Discrepancies:       " + run.discrepancies() + "\n"
                + "Unexpected payments: " + run.unexpectedPayments() + "\n\n"
                + "Review the results\n"
                + "View reconciliation report: " + run.reportUrl() + "\n\n"
                + "This link opens your hosted ReconcileIQ dashboard.\n";
    }

    public static String html(RunSummary run) {
        boolean attention = run.needsAttention();
        String pillBg = attention ? "#FFF1CC" : "#E3F5EB";
        String pillFg = attention ? WARNING : SUCCESS;
        String headline = esc(headline(run));
        String url = esc(run.reportUrl());

        StringBuilder h = new StringBuilder(6000);
        h.append("<!DOCTYPE html>\n<html lang=\"en\"><head>\n")
                .append("<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<meta name=\"color-scheme\" content=\"light\">\n")
                .append("<title>").append(esc(subject(run))).append("</title>\n</head>\n")
                .append("<body style=\"margin:0;padding:0;background:#F1F5F9;\">\n")

                // inbox preview text
                .append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;\">")
                .append(esc(run.discrepancies() + " discrepancies and " + run.unexpectedPayments()
                        + " unexpected payments across " + run.totalOrders() + " orders."))
                .append("</div>\n")

                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" bgcolor=\"#F1F5F9\">")
                .append("<tr><td align=\"center\" style=\"padding:32px 16px;\">\n")

                // card
                .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
                .append("style=\"width:100%;max-width:600px;background:#FFFFFF;border:1px solid ").append(LINE)
                .append(";border-radius:16px;overflow:hidden;font-family:Inter,Segoe UI,Helvetica,Arial,sans-serif;\">\n")

                // brand bar
                .append("<tr><td bgcolor=\"").append(NAVY).append("\" style=\"padding:22px 32px;\">")
                .append("<span style=\"font-size:20px;font-weight:800;letter-spacing:-0.4px;color:#FFFFFF;\">Reconcile</span>")
                .append("<span style=\"font-size:20px;font-weight:800;letter-spacing:-0.4px;color:").append(GOLD).append(";\">IQ</span>")
                .append("<span style=\"float:right;font-size:11px;font-weight:700;letter-spacing:1.5px;color:#B3C6E7;line-height:26px;\">PAYMENTS</span>")
                .append("</td></tr>\n")
                .append("<tr><td bgcolor=\"").append(BLUE).append("\" height=\"4\" style=\"height:4px;line-height:4px;font-size:0;\">&nbsp;</td></tr>\n")

                // headline
                .append("<tr><td style=\"padding:32px 32px 8px;\">")
                .append("<span style=\"display:inline-block;padding:6px 12px;border-radius:999px;background:").append(pillBg)
                .append(";color:").append(pillFg).append(";font-size:12px;font-weight:700;letter-spacing:0.3px;\">&#9679;&nbsp; ")
                .append(headline).append("</span>")
                .append("<h1 style=\"margin:16px 0 8px;font-size:26px;line-height:1.25;color:").append(NAVY).append(";\">")
                .append(esc(subject(run))).append("</h1>")
                .append("<p style=\"margin:0;font-size:15px;line-height:1.6;color:").append(INK).append(";\">")
                .append("A reconciliation run has been completed.</p>")
                .append("<p style=\"margin:6px 0 0;font-size:13px;color:").append(SLATE).append(";\">")
                .append(esc(whenLine(run))).append("</p>")
                .append("</td></tr>\n")

                // metrics
                .append("<tr><td style=\"padding:24px 32px 8px;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
                .append("style=\"border:1px solid ").append(LINE).append(";border-radius:12px;border-collapse:separate;overflow:hidden;\">")
                .append("<tr bgcolor=\"").append(SURFACE).append("\">")
                .append(headCell("Metric", "left")).append(headCell("Count", "right")).append("</tr>")
                .append(metricRow("Total orders", run.totalOrders(), NAVY, false))
                .append(metricRow("Matched", run.matched(), SUCCESS, true))
                .append(metricRow("Discrepancies", run.discrepancies(), DANGER, true))
                .append(metricRow("Unexpected payments", run.unexpectedPayments(), WARNING, true))
                .append("</table></td></tr>\n")

                // call to action
                .append("<tr><td style=\"padding:28px 32px 4px;\">")
                .append("<h2 style=\"margin:0 0 6px;font-size:18px;color:").append(NAVY).append(";\">Review the results</h2>")
                .append("<p style=\"margin:0 0 20px;font-size:14px;line-height:1.6;color:").append(SLATE).append(";\">")
                .append("Open the run to see every record, run an AI investigation and approve or dismiss each finding.</p>")
                .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>")
                .append("<td bgcolor=\"").append(NAVY).append("\" style=\"border-radius:8px;\">")
                .append("<a href=\"").append(url).append("\" target=\"_blank\" style=\"display:inline-block;padding:14px 26px;")
                .append("font-size:15px;font-weight:700;color:#FFFFFF;text-decoration:none;border-radius:8px;\">")
                .append("View reconciliation report &rarr;</a></td></tr></table>")
                .append("<p style=\"margin:16px 0 0;font-size:12px;color:").append(SLATE).append(";\">")
                .append("This link opens your hosted ReconcileIQ dashboard.</p>")
                .append("</td></tr>\n")

                // footer
                .append("<tr><td style=\"padding:28px 32px 28px;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>")
                .append("<td style=\"border-top:1px solid ").append(LINE).append(";padding-top:18px;font-size:12px;line-height:1.6;color:")
                .append(SLATE).append(";\">ReconcileIQ &middot; Sandbox data only &middot; No real money movement<br>")
                .append("You are receiving this because a reconciliation run was started.</td></tr></table>")
                .append("</td></tr>\n")

                .append("</table>\n</td></tr></table>\n</body></html>\n");
        return h.toString();
    }

    // ---------- helpers ----------

    private static String whenLine(RunSummary run) {
        return "Run #" + run.runId()
                + (run.startedAt() == null ? "" : " \u00b7 " + WHEN.format(run.startedAt()));
    }

    private static String headCell(String label, String align) {
        return "<td align=\"" + align + "\" style=\"padding:12px 18px;font-size:11px;font-weight:700;"
                + "letter-spacing:0.8px;text-transform:uppercase;color:" + SLATE + ";\">" + label + "</td>";
    }

    /** Counts are coloured only when they are non-zero, so an all-clear run looks calm. */
    private static String metricRow(String label, int count, String color, boolean muteZero) {
        String shown = muteZero && count == 0 ? SLATE : color;
        return "<tr>"
                + "<td style=\"padding:16px 18px;border-top:1px solid " + LINE + ";font-size:15px;color:" + INK
                + ";border-left:4px solid " + shown + ";\">" + esc(label) + "</td>"
                + "<td align=\"right\" style=\"padding:16px 18px;border-top:1px solid " + LINE
                + ";font-size:22px;font-weight:800;color:" + shown + ";\">" + count + "</td>"
                + "</tr>";
    }

    static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
