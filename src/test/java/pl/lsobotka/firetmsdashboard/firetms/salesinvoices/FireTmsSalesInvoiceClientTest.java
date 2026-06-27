package pl.lsobotka.firetmsdashboard.firetms.salesinvoices;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.lsobotka.firetmsdashboard.firetms.FireTmsProperties;

class FireTmsSalesInvoiceClientTest {

    @Test
    void buildsIssuedSalesInvoicesUriWithIssueDateRange() {
        FireTmsSalesInvoiceClient client = new FireTmsSalesInvoiceClient(
                RestClient.builder().build(),
                new ObjectMapper(),
                new FireTmsProperties("https://app.firetms.com/api"));

        URI uri = client.buildIssuedSalesInvoicesUri(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(uri.toString())
                .isEqualTo(
                        "https://app.firetms.com/api/invoices/sales/issued"
                                + "?dateOfIssueFrom=2026-06-01"
                                + "&dateOfIssueTo=2026-06-30");
    }
}
