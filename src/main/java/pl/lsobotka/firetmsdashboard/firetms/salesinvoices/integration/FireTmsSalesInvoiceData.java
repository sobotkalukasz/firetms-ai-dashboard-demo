package pl.lsobotka.firetmsdashboard.firetms.salesinvoices.integration;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FireTmsSalesInvoiceData(
        String externalId,
        String invoiceNumber,
        LocalDate issueDate,
        LocalDate saleDate,
        String contractorName,
        BigDecimal netAmount,
        BigDecimal grossAmount,
        String currency,
        String status,
        String rawJson) {
}
