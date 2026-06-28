package pl.lsobotka.firetmsdashboard.firetms.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
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

        URI uri = buildUri(request);

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
            return new FireTmsResponse(rawJson, payload);
        } catch (JsonProcessingException exception) {
            throw new FireTmsClientException("FireTMS returned a response that could not be parsed", exception);
        } catch (RestClientException exception) {
            throw new FireTmsClientException("FireTMS request failed", exception);
        }
    }

    URI buildUri(FireTmsRequest request) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path(request.path());

        request.queryParams().forEach(builder::queryParam);
        return builder.build(true).toUri();
    }
}
