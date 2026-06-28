package pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.persistence.SalesInvoiceEntity;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.persistence.SalesInvoiceRepository;

@ExtendWith(MockitoExtension.class)
class SalesInvoiceAnalyticsServiceTest {

    @Mock
    private SalesInvoiceRepository repository;

    @Test
    void groupsGrossAmountByMonth() {
        when(repository.findAll()).thenReturn(List.of(
                invoice(LocalDate.of(2026, 6, 2), "Acme", "ISSUED", "100.50", "EUR"),
                invoice(LocalDate.of(2026, 6, 18), "Beta", "PAID", "50.25", "PLN"),
                invoice(LocalDate.of(2026, 5, 10), "Gamma", "ISSUED", "20.00", "EUR"),
                invoice(null, "Missing date", "DRAFT", "10.00", null)));

        SalesInvoiceAnalyticsService service = new SalesInvoiceAnalyticsService(repository);

        SalesInvoiceAnalyticsSnapshot result = service.loadAnalytics();

        assertThat(result.monthlyGrossSales()).containsExactly(
                new MonthlyGrossSales(java.time.YearMonth.of(2026, 6), List.of(
                        new Money(new BigDecimal("100.50"), "EUR"),
                        new Money(new BigDecimal("50.25"), "PLN"))),
                new MonthlyGrossSales(java.time.YearMonth.of(2026, 5), List.of(
                        new Money(new BigDecimal("20.00"), "EUR"))),
                new MonthlyGrossSales(null, List.of(
                        new Money(new BigDecimal("10.00"), Money.UNKNOWN_CURRENCY))));
        verify(repository).findAll();
    }

    @Test
    void groupsGrossAmountByContractor() {
        when(repository.findAll()).thenReturn(List.of(
                invoice(LocalDate.of(2026, 6, 2), "Acme", "ISSUED", "100.00", "EUR"),
                invoice(LocalDate.of(2026, 6, 3), "Acme", "PAID", null, "EUR"),
                invoice(LocalDate.of(2026, 6, 4), "Beta", "PAID", "40.00", "PLN"),
                invoice(LocalDate.of(2026, 6, 5), " ", "DRAFT", "15.50", null)));

        SalesInvoiceAnalyticsService service = new SalesInvoiceAnalyticsService(repository);

        SalesInvoiceAnalyticsSnapshot result = service.loadAnalytics();

        assertThat(result.contractorGrossSales()).containsExactly(
                new ContractorGrossSales("Acme", List.of(new Money(new BigDecimal("100.00"), "EUR"))),
                new ContractorGrossSales("Beta", List.of(new Money(new BigDecimal("40.00"), "PLN"))),
                new ContractorGrossSales(SalesInvoiceAnalyticsService.UNKNOWN_CONTRACTOR,
                        List.of(new Money(new BigDecimal("15.50"), Money.UNKNOWN_CURRENCY))));
    }

    @Test
    void groupsInvoiceCountByStatus() {
        when(repository.findAll()).thenReturn(List.of(
                invoice(LocalDate.of(2026, 6, 2), "Acme", "ISSUED", "100.00", "EUR"),
                invoice(LocalDate.of(2026, 6, 3), "Beta", "ISSUED", "50.00", "EUR"),
                invoice(LocalDate.of(2026, 6, 4), "Gamma", "PAID", "40.00", "PLN"),
                invoice(LocalDate.of(2026, 6, 5), "Delta", null, "15.50", "PLN")));

        SalesInvoiceAnalyticsService service = new SalesInvoiceAnalyticsService(repository);

        SalesInvoiceAnalyticsSnapshot result = service.loadAnalytics();

        assertThat(result.statusInvoiceCounts()).containsExactly(
                new StatusInvoiceCount("ISSUED", 2),
                new StatusInvoiceCount("PAID", 1),
                new StatusInvoiceCount(SalesInvoiceAnalyticsService.UNKNOWN_STATUS, 1));
    }

    private SalesInvoiceEntity invoice(
            LocalDate issueDate, String contractorName, String status, String grossAmount, String currency) {
        SalesInvoiceEntity invoice = new SalesInvoiceEntity();
        invoice.setIssueDate(issueDate);
        invoice.setContractorName(contractorName);
        invoice.setStatus(status);
        invoice.setGrossAmount(grossAmount == null ? null : new BigDecimal(grossAmount));
        invoice.setCurrency(currency);
        return invoice;
    }
}
