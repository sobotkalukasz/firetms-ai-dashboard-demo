package pl.lsobotka.firetmsdashboard.firetms.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.lsobotka.firetmsdashboard.firetms.FireTmsProperties;

class FireTmsClientTest {

    @Test
    void buildsUriFromPathAndQueryParams() {
        FireTmsClient client = new FireTmsClient(
                RestClient.builder().build(),
                new ObjectMapper(),
                new FireTmsProperties("https://app.firetms.com/api"));

        URI uri = client.buildUri(new FireTmsRequest(
                "/invoices/sales/issued",
                Map.of(
                        "dateOfIssueFrom", LocalDate.of(2026, 6, 1),
                        "dateOfIssueTo", LocalDate.of(2026, 6, 30))));

        assertThat(uri.toString()).startsWith("https://app.firetms.com/api/invoices/sales/issued?");
        assertThat(uri.toString()).contains("dateOfIssueFrom=2026-06-01");
        assertThat(uri.toString()).contains("dateOfIssueTo=2026-06-30");
    }
}
