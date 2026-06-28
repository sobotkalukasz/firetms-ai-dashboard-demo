package pl.lsobotka.firetmsdashboard.firetms.salesinvoices.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FireTmsSalesInvoiceMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FireTmsSalesInvoiceMapper mapper = new FireTmsSalesInvoiceMapper();

    @Test
    void mapsKnownIssuedSalesInvoicePayloadShape() throws Exception {
        String rawJson = """
                {
                  "items": [
                    {
                      "documentId": "SI:1",
                      "documentNumber": "FV/1/06/2019",
                      "issuanceDate": "2019-06-28T08:21:08.087+02:00",
                      "sellDate": "2019-06-29T08:20:08.087+02:00",
                      "client": {"companyName": "Agm Group Sp.z.o.o"},
                      "status": "ISSUED",
                      "totalGross": {"amount": 10500, "currencyCode": "EUR"},
                      "totalNet": {"amount": 10000, "currencyCode": "EUR"}
                    }
                  ]
                }
                """;

        List<FireTmsSalesInvoiceData> result = mapper.map(objectMapper.readTree(rawJson));

        assertThat(result).singleElement().satisfies(invoice -> {
            assertThat(invoice.externalId()).isEqualTo("SI:1");
            assertThat(invoice.invoiceNumber()).isEqualTo("FV/1/06/2019");
            assertThat(invoice.issueDate()).isEqualTo(LocalDate.of(2019, 6, 28));
            assertThat(invoice.saleDate()).isEqualTo(LocalDate.of(2019, 6, 29));
            assertThat(invoice.contractorName()).isEqualTo("Agm Group Sp.z.o.o");
            assertThat(invoice.netAmount()).isEqualByComparingTo("10000");
            assertThat(invoice.grossAmount()).isEqualByComparingTo("10500");
            assertThat(invoice.currency()).isEqualTo("EUR");
            assertThat(invoice.status()).isEqualTo("ISSUED");
        });
    }

    @Test
    void skipsItemsWithoutDocumentNumber() throws Exception {
        String rawJson = """
                {
                  "items": [
                    {
                      "documentId": "SI:missing",
                      "client": {"companyName": "No Number"}
                    }
                  ]
                }
                """;

        List<FireTmsSalesInvoiceData> result = mapper.map(objectMapper.readTree(rawJson));

        assertThat(result).isEmpty();
    }
}
