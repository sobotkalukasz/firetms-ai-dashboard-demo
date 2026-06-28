package pl.lsobotka.firetmsdashboard.ai;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.ContractorGrossSales;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.MonthlyGrossSales;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.SalesInvoiceAnalyticsService;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.SalesInvoiceAnalyticsSnapshot;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.StatusInvoiceCount;

@Service
public class AiDashboardPromptService {

    static final int DEFAULT_TOP_CONTRACTORS_LIMIT = 10;
    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern TOP_LIMIT_PATTERN = Pattern.compile("\\btop\\s+(\\d+)\\b");

    private final SalesInvoiceAnalyticsService analyticsService;

    public AiDashboardPromptService(SalesInvoiceAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    public PromptResult handlePrompt(String prompt) {
        PromptRequest request = classifyPrompt(prompt);
        if (request.intent() == PromptIntent.UNKNOWN) {
            return PromptResult.unknown();
        }

        SalesInvoiceAnalyticsSnapshot analytics = analyticsService.loadAnalytics();
        return switch (request.intent()) {
            case GROSS_SALES_BY_MONTH -> PromptResult.forMonthlyGrossSales(analytics.monthlyGrossSales());
            case TOP_CONTRACTORS_BY_GROSS_AMOUNT -> PromptResult.forTopContractors(
                    analytics.contractorGrossSales().stream()
                            .limit(request.limit())
                            .toList(),
                    request.limit());
            case INVOICE_COUNT_BY_STATUS -> PromptResult.forInvoiceCountByStatus(analytics.statusInvoiceCounts());
            case UNKNOWN -> PromptResult.unknown();
        };
    }

    PromptRequest classifyPrompt(String prompt) {
        String normalized = normalize(prompt);
        if (normalized.isBlank()) {
            return new PromptRequest(PromptIntent.UNKNOWN, DEFAULT_TOP_CONTRACTORS_LIMIT);
        }
        if (isGrossSalesByMonthPrompt(normalized)) {
            return new PromptRequest(PromptIntent.GROSS_SALES_BY_MONTH, DEFAULT_TOP_CONTRACTORS_LIMIT);
        }
        if (isTopContractorsPrompt(normalized)) {
            return new PromptRequest(PromptIntent.TOP_CONTRACTORS_BY_GROSS_AMOUNT, extractTopLimit(normalized));
        }
        if (isInvoiceCountByStatusPrompt(normalized)) {
            return new PromptRequest(PromptIntent.INVOICE_COUNT_BY_STATUS, DEFAULT_TOP_CONTRACTORS_LIMIT);
        }
        return new PromptRequest(PromptIntent.UNKNOWN, DEFAULT_TOP_CONTRACTORS_LIMIT);
    }

    private boolean isGrossSalesByMonthPrompt(String normalized) {
        return containsAny(normalized, "month", "monthly")
                && containsAny(normalized, "gross")
                && containsAny(normalized, "sale", "sales");
    }

    private boolean isTopContractorsPrompt(String normalized) {
        return containsAny(normalized, "contractor", "contractors")
                && containsAny(normalized, "gross")
                && containsAny(normalized, "top", "highest", "largest", "biggest");
    }

    private boolean isInvoiceCountByStatusPrompt(String normalized) {
        return containsAny(normalized, "status")
                && containsAny(normalized, "invoice", "invoices")
                && containsAny(normalized, "count", "counts", "number", "how many");
    }

    private int extractTopLimit(String normalized) {
        Matcher matcher = TOP_LIMIT_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return DEFAULT_TOP_CONTRACTORS_LIMIT;
        }
        try {
            int parsed = Integer.parseInt(matcher.group(1));
            return parsed > 0 ? parsed : DEFAULT_TOP_CONTRACTORS_LIMIT;
        } catch (NumberFormatException exception) {
            return DEFAULT_TOP_CONTRACTORS_LIMIT;
        }
    }

    private boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String prompt) {
        if (prompt == null) {
            return "";
        }
        return MULTI_WHITESPACE.matcher(prompt.trim().toLowerCase(Locale.ROOT)).replaceAll(" ");
    }

    public enum PromptIntent {
        GROSS_SALES_BY_MONTH,
        TOP_CONTRACTORS_BY_GROSS_AMOUNT,
        INVOICE_COUNT_BY_STATUS,
        UNKNOWN
    }

    record PromptRequest(PromptIntent intent, int limit) {
    }

    public record PromptResult(
            PromptIntent intent,
            String title,
            String message,
            List<MonthlyGrossSales> monthlyGrossSales,
            List<ContractorGrossSales> contractorGrossSales,
            List<StatusInvoiceCount> statusInvoiceCounts) {

        private static final String FALLBACK_MESSAGE = "Generated using local safe analytics fallback.";
        private static final String UNKNOWN_PROMPT_MESSAGE = """
                Prompt not recognized.

                Try one of these examples:
                Show gross sales by month
                Show top 10 contractors by gross amount
                Show invoice count by status
                """;

        static PromptResult forMonthlyGrossSales(List<MonthlyGrossSales> monthlyGrossSales) {
            return new PromptResult(
                    PromptIntent.GROSS_SALES_BY_MONTH,
                    "Gross sales by month",
                    FALLBACK_MESSAGE,
                    List.copyOf(monthlyGrossSales),
                    List.of(),
                    List.of());
        }

        static PromptResult forTopContractors(List<ContractorGrossSales> contractorGrossSales, int limit) {
            return new PromptResult(
                    PromptIntent.TOP_CONTRACTORS_BY_GROSS_AMOUNT,
                    "Top " + limit + " contractors by gross amount",
                    FALLBACK_MESSAGE,
                    List.of(),
                    List.copyOf(contractorGrossSales),
                    List.of());
        }

        static PromptResult forInvoiceCountByStatus(List<StatusInvoiceCount> statusInvoiceCounts) {
            return new PromptResult(
                    PromptIntent.INVOICE_COUNT_BY_STATUS,
                    "Invoice count by status",
                    FALLBACK_MESSAGE,
                    List.of(),
                    List.of(),
                    List.copyOf(statusInvoiceCounts));
        }

        static PromptResult unknown() {
            return new PromptResult(
                    PromptIntent.UNKNOWN,
                    "Prompt help",
                    UNKNOWN_PROMPT_MESSAGE,
                    List.of(),
                    List.of(),
                    List.of());
        }

        public boolean recognized() {
            return intent != PromptIntent.UNKNOWN;
        }
    }
}
