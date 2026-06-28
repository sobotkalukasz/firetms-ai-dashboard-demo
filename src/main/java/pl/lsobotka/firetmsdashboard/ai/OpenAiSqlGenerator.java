package pl.lsobotka.firetmsdashboard.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
public class OpenAiSqlGenerator {

    private static final Logger log = LoggerFactory.getLogger(OpenAiSqlGenerator.class);
    private static final String RESPONSE_FORMAT_NAME = "ai_dashboard_sql_generation";
    private static final int MAX_GENERATION_ATTEMPTS = 2;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiOpenAiProperties properties;
    private final SqlSafetyValidator sqlSafetyValidator;

    public OpenAiSqlGenerator(
            @Qualifier("openAiRestClient") RestClient openAiRestClient,
            ObjectMapper objectMapper,
            AiOpenAiProperties properties,
            SqlSafetyValidator sqlSafetyValidator) {
        this.restClient = openAiRestClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.sqlSafetyValidator = sqlSafetyValidator;
    }

    public AiSqlGenerationResult generate(String apiKey, String prompt) {
        return generate(apiKey, prompt, null, null, MAX_GENERATION_ATTEMPTS);
    }

    public AiSqlGenerationResult correct(String apiKey, String prompt, String invalidSql, String correctionFeedback) {
        return generate(apiKey, prompt, invalidSql, correctionFeedback, 1);
    }

    private AiSqlGenerationResult generate(
            String apiKey,
            String prompt,
            String previousSql,
            String validationError,
            int maxAttempts) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("OpenAI API key must not be blank");
        }
        if (!StringUtils.hasText(prompt)) {
            throw new IllegalArgumentException("Prompt must not be blank");
        }

        log.info("Attempting OpenAI SQL generation for /ai-dashboard with promptLength={}", prompt.length());

        String currentPreviousSql = previousSql;
        String currentValidationError = validationError;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String responseBody = restClient.post()
                        .uri("/responses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(buildRequestBody(prompt, currentPreviousSql, currentValidationError))
                        .retrieve()
                        .body(String.class);

                if (responseBody == null) {
                    throw new OpenAiSqlGenerationException("OpenAI returned an empty SQL generation response.");
                }

                AiSqlGenerationResult result = parseGenerationJson(extractGenerationJson(responseBody));
                currentPreviousSql = result.sql();
                String validatedSql = sqlSafetyValidator.validateAndNormalize(result.sql());
                log.info("OpenAI SQL generation completed with title={} attempt={}", result.title(), attempt);
                return new AiSqlGenerationResult(validatedSql, result.title(), result.explanation());
            } catch (SqlValidationException exception) {
                currentValidationError = exception.getMessage();
                log.warn("OpenAI SQL generation returned invalid SQL on attempt {}: {}", attempt, currentValidationError);
                if (attempt == maxAttempts) {
                    throw exception;
                }
            } catch (RestClientResponseException exception) {
                throw new OpenAiSqlGenerationException(sanitizeApiError(exception), exception);
            } catch (JsonProcessingException exception) {
                throw new OpenAiSqlGenerationException("OpenAI returned SQL JSON that could not be parsed.", exception);
            } catch (RestClientException exception) {
                throw new OpenAiSqlGenerationException("OpenAI SQL generation failed. Check the API key and try again later.",
                        exception);
            }
        }

        throw new OpenAiSqlGenerationException("OpenAI SQL generation could not produce valid SQL.");
    }

    String buildRequestBody(String prompt, String previousSql, String validationError) throws JsonProcessingException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.model());
        root.set("input", buildInput(prompt, previousSql, validationError));
        root.put("max_output_tokens", 400);

        ObjectNode text = root.putObject("text");
        ObjectNode format = text.putObject("format");
        format.put("type", "json_schema");
        format.put("name", RESPONSE_FORMAT_NAME);
        format.put("strict", true);
        format.set("schema", buildResponseSchema());

        return objectMapper.writeValueAsString(root);
    }

    AiSqlGenerationResult parseGenerationJson(String generationJson) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(generationJson);
        String sql = root.path("sql").asText("").trim();
        String title = root.path("title").asText("").trim();
        String explanation = root.path("explanation").asText("").trim();

        if (!StringUtils.hasText(sql)) {
            throw new OpenAiSqlGenerationException("OpenAI returned SQL generation output without SQL.");
        }
        if (!StringUtils.hasText(title)) {
            throw new OpenAiSqlGenerationException("OpenAI returned SQL generation output without a title.");
        }
        if (!StringUtils.hasText(explanation)) {
            throw new OpenAiSqlGenerationException("OpenAI returned SQL generation output without an explanation.");
        }

        return new AiSqlGenerationResult(sql, title, explanation);
    }

    String extractGenerationJson(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode output = root.path("output");
        if (!output.isArray()) {
            throw new OpenAiSqlGenerationException("OpenAI returned an unexpected response shape.");
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

        throw new OpenAiSqlGenerationException("OpenAI returned a response without structured SQL content.");
    }

    private ArrayNode buildInput(String prompt, String previousSql, String validationError) {
        ArrayNode input = objectMapper.createArrayNode();
        input.add(createMessage("system", """
                Generate SQL for the user's dashboard request.

                %s

                Hard requirements:
                - use the exact view name ai_sales_invoice_view in the FROM clause
                - never reference sales_invoice or any other table or view
                - never use JOIN
                - generate SQL that is valid for H2
                - prefer simple H2-compatible expressions and aliases
                - never use reserved SQL keywords as aliases such as month
                - when ordering grouped results, prefer repeating the expression or use a safe alias like month_value

                Expected JSON output:
                {
                  "sql": "select ... from ai_sales_invoice_view ... limit 100",
                  "title": "Short title",
                  "explanation": "Short explanation"
                }

                Return JSON only. Never request more schema. Never mention hidden data. Never fabricate unavailable columns.
                """.formatted(sqlSafetyValidator.schemaDescription())));
        if (StringUtils.hasText(previousSql) && StringUtils.hasText(validationError)) {
            input.add(createMessage("system", """
                    The previous SQL was invalid and must be corrected.

                    Invalid SQL:
                    %s

                    Validation error:
                    %s

                    Return a corrected JSON object only.
                    """.formatted(previousSql, validationError)));
        }
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

        ObjectNode sql = propertiesNode.putObject("sql");
        sql.put("type", "string");
        sql.put("minLength", 1);
        sql.put("maxLength", 4000);

        ObjectNode title = propertiesNode.putObject("title");
        title.put("type", "string");
        title.put("minLength", 1);
        title.put("maxLength", 120);

        ObjectNode explanation = propertiesNode.putObject("explanation");
        explanation.put("type", "string");
        explanation.put("minLength", 1);
        explanation.put("maxLength", 300);

        ArrayNode required = schema.putArray("required");
        required.add("sql");
        required.add("title");
        required.add("explanation");
        schema.put("additionalProperties", false);
        return schema;
    }

    private String sanitizeApiError(RestClientResponseException exception) {
        String errorMessage = extractErrorMessage(exception.getResponseBodyAsString());
        int status = exception.getStatusCode().value();

        if (status == 401 || status == 403) {
            return "OpenAI rejected the request. Check the API key and try again.";
        }
        if (status == 429) {
            return "OpenAI rate limits prevented SQL generation. Try again later.";
        }
        if (isModelError(errorMessage)) {
            return "The configured OpenAI model is unavailable for this API key. Check the configured model and try again.";
        }
        return "OpenAI SQL generation could not be completed. Check the API key and try again later.";
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
