package pl.lsobotka.firetmsdashboard.firetms.salesinvoices.integration;

import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Component;
import pl.lsobotka.firetmsdashboard.firetms.integration.FireTmsClient;
import pl.lsobotka.firetmsdashboard.firetms.integration.FireTmsRequest;
import pl.lsobotka.firetmsdashboard.firetms.integration.FireTmsResponse;

@Component
public class IssuedSalesInvoicesGateway {

    private static final String SALES_INVOICES_PATH = "/invoices/sales/issued";

    private final FireTmsClient client;

    public IssuedSalesInvoicesGateway(FireTmsClient client) {
        this.client = client;
    }

    public FireTmsIssuedSalesInvoicesResponse fetchIssuedSalesInvoices(String apiKey, LocalDate dateFrom, LocalDate dateTo) {
        // TODO: FireTMS OpenAPI describes these as date-time fields, but the live endpoint
        // currently accepts plain ISO dates such as 2026-05-20 for this resource.
        FireTmsResponse response = client.get(apiKey, new FireTmsRequest(
                SALES_INVOICES_PATH,
                Map.of(
                        "dateOfIssueFrom", dateFrom,
                        "dateOfIssueTo", dateTo)));

        return new FireTmsIssuedSalesInvoicesResponse(
                response.rawJson(),
                readOptionalInt(response.payload(), "totalItems"),
                response.payload().path("items").isArray() ? response.payload().path("items").size() : null,
                response.payload());
    }

    FireTmsRequest buildRequest(LocalDate dateFrom, LocalDate dateTo) {
        return new FireTmsRequest(
                SALES_INVOICES_PATH,
                Map.of(
                        "dateOfIssueFrom", dateFrom,
                        "dateOfIssueTo", dateTo));
    }

    private Integer readOptionalInt(com.fasterxml.jackson.databind.JsonNode node, String fieldName) {
        if (!node.has(fieldName) || !node.get(fieldName).canConvertToInt()) {
            return null;
        }
        return node.get(fieldName).intValue();
    }
}
