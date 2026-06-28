package pl.lsobotka.firetmsdashboard.firetms.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import pl.lsobotka.firetmsdashboard.firetms.FireTmsClientException;
import pl.lsobotka.firetmsdashboard.firetms.FireTmsProperties;

public class FireTmsClient {

    private static final String API_KEY_HEADER = "apikey";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final FireTmsProperties properties;

    public FireTmsClient(RestClient restClient, ObjectMapper objectMapper, FireTmsProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public FireTmsResponse get(String apiKey, FireTmsRequest request) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("API key must not be blank");
        }

        try {
            FireTmsResponse firstPage = getSinglePage(apiKey, request);
            JsonNode mergedPayload = mergePagedPayload(apiKey, request, firstPage.payload());
            String mergedRawJson = objectMapper.writeValueAsString(mergedPayload);
            return new FireTmsResponse(mergedRawJson, mergedPayload);
        } catch (JsonProcessingException exception) {
            throw new FireTmsClientException("FireTMS returned a response that could not be parsed", exception);
        } catch (RestClientException exception) {
            throw new FireTmsClientException("FireTMS request failed", exception);
        }
    }

    private FireTmsResponse getSinglePage(String apiKey, FireTmsRequest request) throws JsonProcessingException {
        URI uri = buildUri(request);
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
        return new FireTmsResponse(rawJson, payload);
    }

    private JsonNode mergePagedPayload(String apiKey, FireTmsRequest request, JsonNode firstPayload)
            throws JsonProcessingException {
        if (!(firstPayload instanceof ObjectNode payloadObject)) {
            return firstPayload;
        }

        JsonNode firstItems = payloadObject.path("items");
        Integer totalItems = readOptionalInt(payloadObject, "totalItems");
        Integer maxPageNumber = readOptionalInt(payloadObject, "maxPageNumber");
        Integer currentPageNumber = readOptionalInt(payloadObject.path("paging"), "pageNumber");
        if (!firstItems.isArray() || totalItems == null || totalItems <= firstItems.size()) {
            return firstPayload;
        }
        if (maxPageNumber == null || currentPageNumber == null || currentPageNumber >= maxPageNumber) {
            return firstPayload;
        }

        ArrayNode mergedItems = objectMapper.createArrayNode();
        mergedItems.addAll((ArrayNode) firstItems);

        int nextPageNumber = currentPageNumber + 1;
        while (mergedItems.size() < totalItems && nextPageNumber <= maxPageNumber) {
            FireTmsResponse nextPage = getSinglePage(apiKey, withPage(request, nextPageNumber));
            JsonNode nextItems = nextPage.payload().path("items");
            if (!nextItems.isArray() || nextItems.isEmpty()) {
                break;
            }
            mergedItems.addAll((ArrayNode) nextItems);
            currentPageNumber = readOptionalInt(nextPage.payload().path("paging"), "pageNumber");
            if (currentPageNumber == null) {
                break;
            }
            nextPageNumber = currentPageNumber + 1;
        }

        payloadObject.set("items", mergedItems);
        return payloadObject;
    }

    private FireTmsRequest withPage(FireTmsRequest request, int page) {
        Map<String, Object> queryParams = new LinkedHashMap<>(request.queryParams());
        queryParams.put("page", page);
        return new FireTmsRequest(request.path(), queryParams);
    }

    private Integer readOptionalInt(JsonNode node, String fieldName) {
        if (!node.has(fieldName) || !node.get(fieldName).canConvertToInt()) {
            return null;
        }
        return node.get(fieldName).intValue();
    }

    URI buildUri(FireTmsRequest request) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path(request.path());

        request.queryParams().forEach(builder::queryParam);
        return builder.build(true).toUri();
    }
}
