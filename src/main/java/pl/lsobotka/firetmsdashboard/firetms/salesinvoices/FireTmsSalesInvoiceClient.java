package pl.lsobotka.firetmsdashboard.firetms.salesinvoices;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import pl.lsobotka.firetmsdashboard.firetms.FireTmsClientException;
import pl.lsobotka.firetmsdashboard.firetms.FireTmsProperties;

public class FireTmsSalesInvoiceClient {

    private static final String API_KEY_HEADER = "apikey";
    private static final String SALES_INVOICES_PATH = "/invoices/sales/issued";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final FireTmsProperties properties;

    public FireTmsSalesInvoiceClient(RestClient restClient, ObjectMapper objectMapper, FireTmsProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public FireTmsIssuedSalesInvoicesResponse fetchIssuedSalesInvoices(String apiKey, LocalDate dateFrom, LocalDate dateTo) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("API key must not be blank");
        }

        URI uri = buildIssuedSalesInvoicesUri(dateFrom, dateTo);

        try {
            String rawJson = restClient.get()
                    .uri(uri)
                    .header(API_KEY_HEADER, apiKey)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(String.class);

            if (rawJson == null) {
                throw new FireTmsClientException("FireTMS returned an empty response");
            }

            JsonNode payload = objectMapper.readTree(rawJson);
            return new FireTmsIssuedSalesInvoicesResponse(
                    rawJson,
                    readOptionalInt(payload, "totalItems"),
                    payload.path("items").isArray() ? payload.path("items").size() : null,
                    payload);
        } catch (JsonProcessingException exception) {
            throw new FireTmsClientException("FireTMS returned a response that could not be parsed", exception);
        } catch (RestClientException exception) {
            throw new FireTmsClientException("FireTMS request failed", exception);
        }
    }

    URI buildIssuedSalesInvoicesUri(LocalDate dateFrom, LocalDate dateTo) {
        // TODO: FireTMS OpenAPI describes these as date-time fields, but the live endpoint
        // currently accepts plain ISO dates such as 2026-05-20 for this resource.
        return UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path(SALES_INVOICES_PATH)
                .queryParam("dateOfIssueFrom", dateFrom)
                .queryParam("dateOfIssueTo", dateTo)
                .build(true)
                .toUri();
    }

    private Integer readOptionalInt(JsonNode node, String fieldName) {
        if (!node.has(fieldName) || !node.get(fieldName).canConvertToInt()) {
            return null;
        }
        return node.get(fieldName).intValue();
    }
}
