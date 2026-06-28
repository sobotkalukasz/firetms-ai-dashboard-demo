package pl.lsobotka.firetmsdashboard.firetms.salesinvoices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalesInvoiceQueryServiceTest {

    @Mock
    private SalesInvoiceRepository repository;

    @Test
    void loadsFilteredInvoicesFromRepository() {
        SalesInvoice invoice = invoice("INV-2026/01", "Acme Logistics");
        when(repository.findByInvoiceNumberContainingIgnoreCaseOrContractorNameContainingIgnoreCaseOrderByIssueDateDescUpdatedAtDesc(
                "Acme", "Acme")).thenReturn(List.of(invoice));

        SalesInvoiceQueryService service = new SalesInvoiceQueryService(repository);

        List<SalesInvoiceRow> result = service.findSalesInvoices("Acme");

        assertThat(result).singleElement().extracting(SalesInvoiceRow::invoiceNumber).isEqualTo("INV-2026/01");
        verify(repository).findByInvoiceNumberContainingIgnoreCaseOrContractorNameContainingIgnoreCaseOrderByIssueDateDescUpdatedAtDesc(
                "Acme", "Acme");
    }

    @Test
    void loadsAllInvoicesWhenFilterIsBlank() {
        SalesInvoice invoice = invoice("INV-2026/02", "Beta Transport");
        when(repository.findAllByOrderByIssueDateDescUpdatedAtDesc()).thenReturn(List.of(invoice));

        SalesInvoiceQueryService service = new SalesInvoiceQueryService(repository);

        List<SalesInvoiceRow> result = service.findSalesInvoices(" ");

        assertThat(result).hasSize(1);
        verify(repository).findAllByOrderByIssueDateDescUpdatedAtDesc();
    }

    private SalesInvoice invoice(String number, String contractorName) {
        SalesInvoice invoice = new SalesInvoice();
        invoice.setInvoiceNumber(number);
        invoice.setContractorName(contractorName);
        invoice.setIssueDate(LocalDate.of(2026, 6, 20));
        invoice.setSaleDate(LocalDate.of(2026, 6, 18));
        invoice.setUpdatedAt(LocalDateTime.of(2026, 6, 28, 12, 30));
        return invoice;
    }
}
