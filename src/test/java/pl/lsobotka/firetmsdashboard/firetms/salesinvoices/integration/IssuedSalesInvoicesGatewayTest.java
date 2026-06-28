package pl.lsobotka.firetmsdashboard.firetms.salesinvoices.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import pl.lsobotka.firetmsdashboard.firetms.integration.FireTmsClient;
import pl.lsobotka.firetmsdashboard.firetms.integration.FireTmsResponse;

class IssuedSalesInvoicesGatewayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildsRequestForIssuedSalesInvoicesDateRange() {
        FireTmsClient client = mock(FireTmsClient.class);
        IssuedSalesInvoicesGateway gateway = new IssuedSalesInvoicesGateway(client);

        var request = gateway.buildRequest(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(request.path()).isEqualTo("/invoices/sales/issued");
        assertThat(request.queryParams().get("dateOfIssueFrom")).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(request.queryParams().get("dateOfIssueTo")).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void mapsTransportResponseToIssuedSalesInvoicesResponse() throws Exception {
        FireTmsClient client = mock(FireTmsClient.class);
        IssuedSalesInvoicesGateway gateway = new IssuedSalesInvoicesGateway(client);
        String rawJson = "{\"totalItems\":5,\"items\":[{\"documentNumber\":\"1\"},{\"documentNumber\":\"2\"}]}";
        JsonNode payload = objectMapper.readTree(rawJson);
        when(client.get("secret", gateway.buildRequest(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))))
                .thenReturn(new FireTmsResponse(rawJson, payload));

        FireTmsIssuedSalesInvoicesResponse response = gateway.fetchIssuedSalesInvoices(
                "secret", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(response.rawJson()).isEqualTo(rawJson);
        assertThat(response.totalItems()).isEqualTo(5);
        assertThat(response.returnedItems()).isEqualTo(2);
        assertThat(response.payload()).isEqualTo(payload);
    }
}
