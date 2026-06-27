package pl.lsobotka.firetmsdashboard.firetms.salesinvoices;

import com.fasterxml.jackson.databind.JsonNode;

public record FireTmsIssuedSalesInvoicesResponse(
        String rawJson,
        Integer totalItems,
        Integer returnedItems,
        JsonNode payload) {
}
