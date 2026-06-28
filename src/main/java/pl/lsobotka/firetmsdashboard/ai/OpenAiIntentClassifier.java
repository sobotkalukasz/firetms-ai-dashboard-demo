package pl.lsobotka.firetmsdashboard.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.EnumSet;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class OpenAiIntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(OpenAiIntentClassifier.class);
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final String RESPONSE_FORMAT_NAME = "ai_dashboard_intent_classification";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiOpenAiProperties properties;

    public OpenAiIntentClassifier(
            @Qualifier("openAiRestClient") RestClient openAiRestClient,
            ObjectMapper objectMapper,
            AiOpenAiProperties properties) {
        this.restClient = openAiRestClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public AiIntentClassificationResult classify(String apiKey, String prompt) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("OpenAI API key must not be blank");
        }
        if (!StringUtils.hasText(prompt)) {
            throw new IllegalArgumentException("Prompt must not be blank");
        }

        log.info("Attempting OpenAI intent classification for /ai-dashboard with promptLength={}", prompt.length());

        try {
            String responseBody = restClient.post()
                    .uri("/responses")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequestBody(prompt))
                    .retrieve()
                    .body(String.class);

            if (responseBody == null) {
                throw new OpenAiIntentClassificationException("OpenAI returned an empty classification response.");
            }

            String classificationJson = extractClassificationJson(responseBody);
            AiIntentClassificationResult result = parseClassificationJson(classificationJson);
            log.info("OpenAI intent classification completed with intent={}", result.intent());
            return result;
        } catch (RestClientResponseException exception) {
            throw new OpenAiIntentClassificationException(sanitizeApiError(exception), exception);
        } catch (JsonProcessingException exception) {
            throw new OpenAiIntentClassificationException("OpenAI returned a classification response that could not be parsed.",
                    exception);
        } catch (RestClientException exception) {
            throw new OpenAiIntentClassificationException("OpenAI classification request failed. Check the API key and try again later.",
                    exception);
        }
    }

    String buildRequestBody(String prompt) throws JsonProcessingException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.model());
        root.set("input", buildInput(prompt));
        root.put("max_output_tokens", 200);

        ObjectNode text = root.putObject("text");
        ObjectNode format = text.putObject("format");
        format.put("type", "json_schema");
        format.put("name", RESPONSE_FORMAT_NAME);
        format.put("strict", true);
        format.set("schema", buildResponseSchema());

        return objectMapper.writeValueAsString(root);
    }

    AiIntentClassificationResult parseClassificationJson(String classificationJson) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(classificationJson);
        String intentValue = root.path("intent").asText("");
        AiDashboardIntent intent = parseIntent(intentValue);
        int limit = normalizeLimit(root.path("limit").asInt(DEFAULT_LIMIT));
        String reason = root.path("reason").asText("").trim();
        if (!StringUtils.hasText(reason)) {
            throw new OpenAiIntentClassificationException("OpenAI returned a classification response without a reason.");
        }
        return new AiIntentClassificationResult(intent, limit, reason);
    }

    String extractClassificationJson(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode output = root.path("output");
        if (!output.isArray()) {
            throw new OpenAiIntentClassificationException("OpenAI returned an unexpected response shape.");
        }

        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode contentItem : content) {
                JsonNode text = contentItem.get("text");
                if (text != null && text.isTextual() && StringUtils.hasText(text.asText())) {
                    return text.asText();
                }
            }
        }

        throw new OpenAiIntentClassificationException("OpenAI returned a response without structured classification content.");
    }

    private ArrayNode buildInput(String prompt) {
        ArrayNode input = objectMapper.createArrayNode();
        input.add(createMessage("system", """
                Classify the user's dashboard request into exactly one allowed intent.
                Allowed intents:
                - GROSS_SALES_BY_MONTH
                - TOP_CONTRACTORS_BY_GROSS_AMOUNT
                - INVOICE_COUNT_BY_STATUS
                - UNKNOWN

                Return JSON only. Do not generate SQL. Do not request or assume invoice row data.
                """));
        input.add(createMessage("user", prompt));
        return input;
    }

    private ObjectNode createMessage(String role, String text) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", role);
        ArrayNode content = message.putArray("content");
        ObjectNode inputText = content.addObject();
        inputText.put("type", "input_text");
        inputText.put("text", text);
        return message;
    }

    private ObjectNode buildResponseSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode propertiesNode = schema.putObject("properties");
        ObjectNode intent = propertiesNode.putObject("intent");
        intent.put("type", "string");
        ArrayNode enumValues = intent.putArray("enum");
        for (AiDashboardIntent value : EnumSet.allOf(AiDashboardIntent.class)) {
            enumValues.add(value.name());
        }

        ObjectNode limit = propertiesNode.putObject("limit");
        limit.put("type", "integer");
        limit.put("minimum", 1);
        limit.put("maximum", MAX_LIMIT);

        ObjectNode reason = propertiesNode.putObject("reason");
        reason.put("type", "string");
        reason.put("minLength", 1);
        reason.put("maxLength", 240);

        ArrayNode required = schema.putArray("required");
        required.add("intent");
        required.add("limit");
        required.add("reason");
        schema.put("additionalProperties", false);
        return schema;
    }

    private AiDashboardIntent parseIntent(String value) {
        if (!StringUtils.hasText(value)) {
            throw new OpenAiIntentClassificationException("OpenAI returned a classification response without an intent.");
        }

        try {
            return AiDashboardIntent.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new OpenAiIntentClassificationException("OpenAI returned an unsupported dashboard intent.");
        }
    }

    private int normalizeLimit(int limit) {
        if (limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String sanitizeApiError(RestClientResponseException exception) {
        String errorMessage = extractErrorMessage(exception.getResponseBodyAsString());
        int status = exception.getStatusCode().value();

        if (status == 401 || status == 403) {
            return "OpenAI rejected the request. Check the API key and try again.";
        }
        if (status == 429) {
            return "OpenAI rate limits prevented classification. Try again later.";
        }
        if (isModelError(errorMessage)) {
            return "The configured OpenAI model is unavailable for this API key. Check the configured model and try again.";
        }
        return "OpenAI classification could not be completed. Check the API key and try again later.";
    }

    private String extractErrorMessage(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("error").path("message").asText("");
        } catch (JsonProcessingException exception) {
            return "";
        }
    }

    private boolean isModelError(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return false;
        }
        String normalized = errorMessage.toLowerCase(Locale.ROOT);
        return normalized.contains("model")
                && (normalized.contains("does not exist")
                        || normalized.contains("not found")
                        || normalized.contains("not available")
                        || normalized.contains("unsupported"));
    }
}
