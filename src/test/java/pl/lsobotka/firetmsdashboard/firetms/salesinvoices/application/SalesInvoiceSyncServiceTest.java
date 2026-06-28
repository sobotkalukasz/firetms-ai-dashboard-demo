package pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application;

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
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.integration.FireTmsIssuedSalesInvoicesResponse;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.integration.FireTmsSalesInvoiceMapper;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.integration.IssuedSalesInvoicesGateway;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.persistence.SalesInvoiceEntity;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.persistence.SalesInvoiceRepository;

@ExtendWith(MockitoExtension.class)
class SalesInvoiceSyncServiceTest {

    @Mock
    private IssuedSalesInvoicesGateway gateway;

    @Mock
    private SalesInvoiceRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FireTmsSalesInvoiceMapper mapper = new FireTmsSalesInvoiceMapper();

    @Test
    void persistsInvoicesFromResponseItems() throws Exception {
        String rawJson = """
                {
                  "totalItems": 1,
                  "items": [
                    {
                      "documentId": "SI:0872cef0-1749-4e20-8507-9d966f0292d3",
                      "documentNumber": "0002/06/2019",
                      "issuanceDate": "2019-06-28T08:21:08.087+02:00",
                      "sellDate": "2019-06-29T08:20:08.087+02:00",
                      "client": {"companyName": "Agm Group Sp.z.o.o"},
                      "status": "ISSUED",
                      "totalGross": {"amount": 10500, "currencyCode": "EUR"},
                      "totalNet": {"amount": 10500, "currencyCode": "EUR"}
                    }
                  ]
                }
                """;
        FireTmsIssuedSalesInvoicesResponse response = new FireTmsIssuedSalesInvoicesResponse(
                rawJson, 1, 1, objectMapper.readTree(rawJson));
        when(gateway.fetchIssuedSalesInvoices("secret", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
                .thenReturn(response);
        when(repository.findByExternalId("SI:0872cef0-1749-4e20-8507-9d966f0292d3")).thenReturn(Optional.empty());
        when(repository.save(any(SalesInvoiceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SalesInvoiceSyncService service = new SalesInvoiceSyncService(
                gateway,
                mapper,
                repository,
                Clock.fixed(Instant.parse("2026-06-28T10:15:30Z"), ZoneOffset.UTC));

        SalesInvoiceSyncResult result = service.syncIssuedSalesInvoices(
                "secret", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        ArgumentCaptor<SalesInvoiceEntity> captor = ArgumentCaptor.forClass(SalesInvoiceEntity.class);
        verify(repository).save(captor.capture());

        SalesInvoiceEntity invoice = captor.getValue();
        assertThat(result.fetchedInvoices()).isEqualTo(1);
        assertThat(result.insertedInvoices()).isEqualTo(1);
        assertThat(result.updatedInvoices()).isZero();
        assertThat(result.persistedInvoices()).isEqualTo(1);
        assertThat(invoice.getExternalId()).isEqualTo("SI:0872cef0-1749-4e20-8507-9d966f0292d3");
        assertThat(invoice.getInvoiceNumber()).isEqualTo("0002/06/2019");
        assertThat(invoice.getContractorName()).isEqualTo("Agm Group Sp.z.o.o");
        assertThat(invoice.getCurrency()).isEqualTo("EUR");
        assertThat(invoice.getStatus()).isEqualTo("ISSUED");
        assertThat(invoice.getIssueDate()).isEqualTo(LocalDate.of(2019, 6, 28));
        assertThat(invoice.getSaleDate()).isEqualTo(LocalDate.of(2019, 6, 29));
        assertThat(invoice.getUpdatedAt()).isEqualTo(java.time.LocalDateTime.of(2026, 6, 28, 10, 15, 30));
        assertThat(invoice.getNetAmount()).isEqualByComparingTo("10500");
        assertThat(invoice.getGrossAmount()).isEqualByComparingTo("10500");
        assertThat(invoice.getRawJson()).contains("\"documentNumber\":\"0002/06/2019\"");
    }

    @Test
    void skipsItemsWithoutInvoiceNumber() throws Exception {
        String rawJson = """
                {
                  "items": [
                    {
                      "documentId": "fire-2",
                      "client": {"companyName": "Missing Number"}
                    }
                  ]
                }
                """;
        FireTmsIssuedSalesInvoicesResponse response = new FireTmsIssuedSalesInvoicesResponse(
                rawJson, null, 1, objectMapper.readTree(rawJson));
        when(gateway.fetchIssuedSalesInvoices("secret", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
                .thenReturn(response);

        SalesInvoiceSyncService service = new SalesInvoiceSyncService(gateway, mapper, repository, Clock.systemUTC());

        SalesInvoiceSyncResult result = service.syncIssuedSalesInvoices(
                "secret", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(result.fetchedInvoices()).isEqualTo(1);
        assertThat(result.insertedInvoices()).isZero();
        assertThat(result.updatedInvoices()).isZero();
        assertThat(result.persistedInvoices()).isZero();
    }

    @Test
    void updatesExistingInvoiceWhenDocumentIdMatches() throws Exception {
        String rawJson = """
                {
                  "items": [
                    {
                      "documentId": "SI:existing",
                      "documentNumber": "0003/06/2019",
                      "issuanceDate": "2019-06-28T08:21:08.087+02:00",
                      "sellDate": "2019-06-29T08:20:08.087+02:00",
                      "client": {"companyName": "Existing Client"},
                      "status": "ISSUED",
                      "totalGross": {"amount": 200, "currencyCode": "PLN"},
                      "totalNet": {"amount": 100, "currencyCode": "PLN"}
                    }
                  ]
                }
                """;
        FireTmsIssuedSalesInvoicesResponse response = new FireTmsIssuedSalesInvoicesResponse(
                rawJson, null, 1, objectMapper.readTree(rawJson));
        SalesInvoiceEntity existing = new SalesInvoiceEntity();
        existing.setExternalId("SI:existing");
        existing.setInvoiceNumber("OLD");

        when(gateway.fetchIssuedSalesInvoices("secret", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
                .thenReturn(response);
        when(repository.findByExternalId("SI:existing")).thenReturn(Optional.of(existing));
        when(repository.save(any(SalesInvoiceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SalesInvoiceSyncService service = new SalesInvoiceSyncService(gateway, mapper, repository, Clock.systemUTC());

        SalesInvoiceSyncResult result = service.syncIssuedSalesInvoices(
                "secret", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(result.fetchedInvoices()).isEqualTo(1);
        assertThat(result.insertedInvoices()).isZero();
        assertThat(result.updatedInvoices()).isEqualTo(1);
        assertThat(existing.getInvoiceNumber()).isEqualTo("0003/06/2019");
        assertThat(existing.getContractorName()).isEqualTo("Existing Client");
    }
}
