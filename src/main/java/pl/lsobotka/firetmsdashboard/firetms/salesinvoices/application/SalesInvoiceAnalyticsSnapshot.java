package pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application;

import java.util.List;

public record SalesInvoiceAnalyticsSnapshot(
        List<MonthlyGrossSales> monthlyGrossSales,
        List<ContractorGrossSales> contractorGrossSales,
        List<StatusInvoiceCount> statusInvoiceCounts) {
}
