package pl.lsobotka.firetmsdashboard.firetms.salesinvoices;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import pl.lsobotka.firetmsdashboard.firetms.FireTmsClientException;
import pl.lsobotka.firetmsdashboard.firetms.FireTmsProperties;

@Component
public class FireTmsSalesInvoiceClient {

    private static final String API_KEY_HEADER = "apikey";
    private static final String SALES_INVOICES_PATH = "/invoices/sales/issued";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final FireTmsProperties properties;
    private final ZoneId zoneId;

    public FireTmsSalesInvoiceClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
            FireTmsProperties properties) {
        this(restClientBuilder.build(), objectMapper, properties, ZoneId.systemDefault());
    }

    FireTmsSalesInvoiceClient(RestClient restClient, ObjectMapper objectMapper, FireTmsProperties properties, ZoneId zoneId) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.zoneId = zoneId;
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
                    readOptionalInt(payload.path("items").isArray() ? payload.path("items").size() : null),
                    payload);
        } catch (JsonProcessingException exception) {
            throw new FireTmsClientException("FireTMS returned a response that could not be parsed", exception);
        } catch (RestClientException exception) {
            throw new FireTmsClientException("FireTMS request failed", exception);
        }
    }

    URI buildIssuedSalesInvoicesUri(LocalDate dateFrom, LocalDate dateTo) {
        // TODO: The initial UI maps the selected range to issue-date filters only.
        // If users need sale-date filtering, expose dateOfSaleFrom/dateOfSaleTo in the UI as a separate option.
        return UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path(SALES_INVOICES_PATH)
                .queryParam("dateOfIssueFrom", formatStartOfDay(dateFrom))
                .queryParam("dateOfIssueTo", formatEndOfDay(dateTo))
                .build(true)
                .toUri();
    }

    private String formatStartOfDay(LocalDate date) {
        OffsetDateTime dateTime = date.atStartOfDay(zoneId).toOffsetDateTime();
        return DATE_TIME_FORMATTER.format(dateTime);
    }

    private String formatEndOfDay(LocalDate date) {
        OffsetDateTime dateTime = date.plusDays(1).atStartOfDay(zoneId).minusNanos(1).toOffsetDateTime();
        return DATE_TIME_FORMATTER.format(dateTime);
    }

    private Integer readOptionalInt(JsonNode node, String fieldName) {
        if (!node.has(fieldName) || !node.get(fieldName).canConvertToInt()) {
            return null;
        }
        return node.get(fieldName).intValue();
    }

    private Integer readOptionalInt(Integer value) {
        return value;
    }
}
