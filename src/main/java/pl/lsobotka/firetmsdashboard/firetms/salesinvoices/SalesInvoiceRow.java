package pl.lsobotka.firetmsdashboard.firetms.salesinvoices;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SalesInvoiceRow(
        Long id,
        String invoiceNumber,
        LocalDate issueDate,
        LocalDate saleDate,
        String contractorName,
        BigDecimal netAmount,
        BigDecimal grossAmount,
        String currency,
        String status,
        LocalDateTime updatedAt) {

    static SalesInvoiceRow from(SalesInvoice invoice) {
        return new SalesInvoiceRow(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getIssueDate(),
                invoice.getSaleDate(),
                invoice.getContractorName(),
                invoice.getNetAmount(),
                invoice.getGrossAmount(),
                invoice.getCurrency(),
                invoice.getStatus(),
                invoice.getUpdatedAt());
    }
}
