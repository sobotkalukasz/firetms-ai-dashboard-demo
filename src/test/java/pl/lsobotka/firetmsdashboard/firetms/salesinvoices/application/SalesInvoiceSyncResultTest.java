package pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.integration.FireTmsIssuedSalesInvoicesResponse;

class SalesInvoiceSyncResultTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildsSuccessMessageWithReturnedAndTotalItems() throws Exception {
        FireTmsIssuedSalesInvoicesResponse response = new FireTmsIssuedSalesInvoicesResponse(
                "{\"items\":[]}", 12, 3, objectMapper.readTree("{\"items\":[]}"));
        SalesInvoiceSyncResult result = new SalesInvoiceSyncResult(response, 3, 1, 2);

        assertThat(result.successMessage())
                .isEqualTo("Sync succeeded. Fetched 3, inserted 1, updated 2. Returned 3 items, totalItems=12.");
    }

    @Test
    void buildsSuccessMessageWithoutTotalItems() throws Exception {
        FireTmsIssuedSalesInvoicesResponse response = new FireTmsIssuedSalesInvoicesResponse(
                "{\"items\":[]}", null, 2, objectMapper.readTree("{\"items\":[]}"));
        SalesInvoiceSyncResult result = new SalesInvoiceSyncResult(response, 2, 2, 0);

        assertThat(result.successMessage())
                .isEqualTo("Sync succeeded. Fetched 2, inserted 2, updated 0. Returned 2 items.");
    }
}
