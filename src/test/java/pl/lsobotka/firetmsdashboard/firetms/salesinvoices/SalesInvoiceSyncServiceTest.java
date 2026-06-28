package pl.lsobotka.firetmsdashboard.firetms.salesinvoices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalesInvoiceSyncServiceTest {

    @Mock
    private FireTmsSalesInvoiceClient client;

    @Mock
    private SalesInvoiceRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void persistsInvoicesFromResponseItems() throws Exception {
        String rawJson = """
                {
                  "totalItems": 1,
                  "items": [
                    {
                      "id": "fire-1",
                      "fullNumber": "FV/2026/06/1",
                      "dateOfIssue": "2026-06-20",
                      "dateOfSale": "2026-06-19",
                      "contractor": {"name": "Acme Logistics"},
                      "netValue": 100.50,
                      "grossValue": 123.62,
                      "currency": "PLN",
                      "status": "PAID"
                    }
                  ]
                }
                """;
        FireTmsIssuedSalesInvoicesResponse response = new FireTmsIssuedSalesInvoicesResponse(
                rawJson, 1, 1, objectMapper.readTree(rawJson));
        when(client.fetchIssuedSalesInvoices("secret", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
                .thenReturn(response);
        when(repository.findByExternalId("fire-1")).thenReturn(Optional.empty());
        when(repository.save(any(SalesInvoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SalesInvoiceSyncService service = new SalesInvoiceSyncService(
                client,
                repository,
                Clock.fixed(Instant.parse("2026-06-28T10:15:30Z"), ZoneOffset.UTC));

        SalesInvoiceSyncResult result = service.syncIssuedSalesInvoices(
                "secret", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        ArgumentCaptor<SalesInvoice> captor = ArgumentCaptor.forClass(SalesInvoice.class);
        verify(repository).save(captor.capture());

        SalesInvoice invoice = captor.getValue();
        assertThat(result.persistedInvoices()).isEqualTo(1);
        assertThat(invoice.getExternalId()).isEqualTo("fire-1");
        assertThat(invoice.getInvoiceNumber()).isEqualTo("FV/2026/06/1");
        assertThat(invoice.getContractorName()).isEqualTo("Acme Logistics");
        assertThat(invoice.getCurrency()).isEqualTo("PLN");
        assertThat(invoice.getStatus()).isEqualTo("PAID");
        assertThat(invoice.getIssueDate()).isEqualTo(LocalDate.of(2026, 6, 20));
        assertThat(invoice.getSaleDate()).isEqualTo(LocalDate.of(2026, 6, 19));
        assertThat(invoice.getUpdatedAt()).isEqualTo(java.time.LocalDateTime.of(2026, 6, 28, 10, 15, 30));
        assertThat(invoice.getRawJson()).contains("\"fullNumber\":\"FV/2026/06/1\"");
    }

    @Test
    void skipsItemsWithoutInvoiceNumber() throws Exception {
        String rawJson = """
                {
                  "items": [
                    {
                      "id": "fire-2",
                      "contractor": {"name": "Missing Number"}
                    }
                  ]
                }
                """;
        FireTmsIssuedSalesInvoicesResponse response = new FireTmsIssuedSalesInvoicesResponse(
                rawJson, null, 1, objectMapper.readTree(rawJson));
        when(client.fetchIssuedSalesInvoices("secret", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
                .thenReturn(response);
        when(repository.findByExternalId("fire-2")).thenReturn(Optional.empty());

        SalesInvoiceSyncService service = new SalesInvoiceSyncService(client, repository, Clock.systemUTC());

        SalesInvoiceSyncResult result = service.syncIssuedSalesInvoices(
                "secret", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(result.persistedInvoices()).isZero();
    }
}
