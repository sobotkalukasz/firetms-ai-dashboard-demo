package pl.lsobotka.firetmsdashboard.firetms.salesinvoices.integration;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FireTmsSalesInvoiceData(
        String externalId,
        String invoiceNumber,
        LocalDate issueDate,
        LocalDate saleDate,
        String contractorName,
        String ksefNumber,
        BigDecimal netAmount,
        BigDecimal grossAmount,
        BigDecimal outstandingToPay,
        String currency,
        String status,
        String rawJson) {
}
