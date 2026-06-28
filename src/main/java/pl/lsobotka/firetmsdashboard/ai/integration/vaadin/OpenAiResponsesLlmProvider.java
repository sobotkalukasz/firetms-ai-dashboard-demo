package pl.lsobotka.firetmsdashboard.ai.integration.vaadin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.ai.common.ChatMessage;
import com.vaadin.flow.component.ai.provider.LLMProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pl.lsobotka.firetmsdashboard.ai.integration.openai.AiOpenAiProperties;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.node.NullNode;

public class OpenAiResponsesLlmProvider implements LLMProvider {

    private static final int MAX_TOOL_ROUNDS = 8;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final tools.jackson.databind.ObjectMapper toolObjectMapper = new tools.jackson.databind.ObjectMapper();
    private final AiOpenAiProperties properties;
    private final Supplier<String> apiKeySupplier;
    private final List<ChatMessage> history = new CopyOnWriteArrayList<>();

    public OpenAiResponsesLlmProvider(
            RestClient restClient,
            ObjectMapper objectMapper,
            AiOpenAiProperties properties,
            Supplier<String> apiKeySupplier) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.apiKeySupplier = Objects.requireNonNull(apiKeySupplier, "apiKeySupplier must not be null");
    }

    @Override
    public Flux<String> stream(LLMRequest request) {
        return Mono.fromCallable(() -> execute(request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(response -> response.isEmpty() ? Flux.empty() : Flux.just(response));
    }

    @Override
    public void setHistory(
            List<ChatMessage> history,
            Map<String, List<com.vaadin.flow.component.ai.common.AIAttachment>> attachmentsByMessageId) {
        this.history.clear();
        this.history.addAll(history);
    }

    private String execute(LLMRequest request) {
        String apiKey = Optional.ofNullable(apiKeySupplier.get()).orElse("").trim();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("OpenAI API key is required for this Vaadin AI experiment.");
        }

        List<ChatMessage> updatedHistory = new ArrayList<>(history);
        updatedHistory.add(new ChatMessage(ChatMessage.Role.USER, request.userMessage(), null, Instant.now()));

        String previousResponseId = null;
        ArrayNode input = buildInitialInput(request, updatedHistory);
        String assistantResponse = "";

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            JsonNode root = callResponsesApi(apiKey, request, input, previousResponseId);
            previousResponseId = root.path("id").asText(null);

            List<FunctionCall> functionCalls = extractFunctionCalls(root.path("output"));
            if (functionCalls.isEmpty()) {
                assistantResponse = extractAssistantText(root.path("output"));
                if (!assistantResponse.isEmpty()) {
                    updatedHistory.add(new ChatMessage(ChatMessage.Role.ASSISTANT, assistantResponse, null, Instant.now()));
                }
                history.clear();
                history.addAll(updatedHistory);
                return assistantResponse;
            }

            input = buildFunctionCallOutputs(functionCalls, request.explicitTools());
        }

        throw new IllegalStateException("OpenAI did not finish the Vaadin AI request within the tool-call limit.");
    }

    private JsonNode callResponsesApi(
            String apiKey,
            LLMRequest request,
            ArrayNode input,
            String previousResponseId) {
        try {
            String responseBody = restClient.post()
                    .uri("/responses")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequestBody(request, input, previousResponseId))
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(responseBody)) {
                throw new IllegalStateException("OpenAI returned an empty response.");
            }
            return objectMapper.readTree(responseBody);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException(sanitizeApiError(exception), exception);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("OpenAI returned a response that could not be parsed.", exception);
        } catch (RestClientException exception) {
            throw new IllegalStateException("OpenAI dashboard generation could not be completed. Check the API key and try again later.",
                    exception);
        }
    }

    private String buildRequestBody(LLMRequest request, ArrayNode input, String previousResponseId)
            throws JsonProcessingException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.model());
        root.set("input", input);
        if (previousResponseId != null) {
            root.put("previous_response_id", previousResponseId);
        }
        if (!request.explicitTools().isEmpty()) {
            root.set("tools", buildTools(request.explicitTools()));
            root.put("tool_choice", "auto");
            root.put("parallel_tool_calls", true);
        }
        return objectMapper.writeValueAsString(root);
    }

    ArrayNode buildInitialInput(LLMRequest request, List<ChatMessage> conversation) {
        ArrayNode input = objectMapper.createArrayNode();
        if (StringUtils.hasText(request.systemPrompt())) {
            input.add(createMessage("system", "input_text", request.systemPrompt()));
        }
        for (ChatMessage message : conversation) {
            input.add(createMessage(
                    roleName(message.role()),
                    contentType(message.role()),
                    message.content()));
        }
        return input;
    }

    private ObjectNode createMessage(String role, String contentType, String text) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", role);
        ArrayNode content = message.putArray("content");
        ObjectNode contentItem = content.addObject();
        contentItem.put("type", contentType);
        contentItem.put("text", text);
        return message;
    }

    private String roleName(ChatMessage.Role role) {
        return switch (role) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            default -> "system";
        };
    }

    private String contentType(ChatMessage.Role role) {
        return switch (role) {
            case ASSISTANT -> "output_text";
            default -> "input_text";
        };
    }

    private ArrayNode buildTools(List<ToolSpec> toolSpecs) {
        ArrayNode tools = objectMapper.createArrayNode();
        for (ToolSpec toolSpec : toolSpecs) {
            ObjectNode tool = tools.addObject();
            tool.put("type", "function");
            tool.put("name", toolSpec.getName());
            tool.put("description", toolSpec.getDescription());
            String schema = toolSpec.getParametersSchema();
            if (schema != null) {
                try {
                    tool.set("parameters", objectMapper.readTree(schema));
                } catch (JsonProcessingException exception) {
                    throw new IllegalStateException("Tool schema for " + toolSpec.getName() + " could not be parsed.", exception);
                }
            } else {
                ObjectNode parameters = tool.putObject("parameters");
                parameters.put("type", "object");
                parameters.putObject("properties");
                parameters.putArray("required");
                parameters.put("additionalProperties", false);
            }
        }
        return tools;
    }

    private List<FunctionCall> extractFunctionCalls(JsonNode output) {
        List<FunctionCall> functionCalls = new ArrayList<>();
        if (!output.isArray()) {
            return functionCalls;
        }
        for (JsonNode item : output) {
            if ("function_call".equals(item.path("type").asText())) {
                functionCalls.add(new FunctionCall(
                        item.path("call_id").asText(""),
                        item.path("name").asText(""),
                        item.path("arguments").asText("{}")));
            }
        }
        return functionCalls;
    }

    private ArrayNode buildFunctionCallOutputs(List<FunctionCall> calls, List<ToolSpec> toolSpecs) {
        ArrayNode input = objectMapper.createArrayNode();
        for (FunctionCall call : calls) {
            ObjectNode toolOutput = input.addObject();
            toolOutput.put("type", "function_call_output");
            toolOutput.put("call_id", call.callId());
            toolOutput.put("output", executeTool(call, toolSpecs));
        }
        return input;
    }

    private String executeTool(FunctionCall call, List<ToolSpec> toolSpecs) {
        ToolSpec tool = toolSpecs.stream()
                .filter(candidate -> candidate.getName().equals(call.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown tool requested by OpenAI: " + call.name()));
        try {
            tools.jackson.databind.JsonNode arguments = call.arguments().isBlank()
                    ? NullNode.instance
                    : toolObjectMapper.readTree(call.arguments());
            return tool.execute(arguments);
        } catch (RuntimeException exception) {
            return "ERROR: " + exception.getMessage();
        }
    }

    private String extractAssistantText(JsonNode output) {
        if (!output.isArray()) {
            throw new IllegalStateException("OpenAI returned an unexpected response shape.");
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode item : output) {
            if (!"message".equals(item.path("type").asText())) {
                continue;
            }
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode contentItem : content) {
                JsonNode text = contentItem.get("text");
                if (text != null && text.isTextual()) {
                    builder.append(text.asText());
                }
            }
        }
        return builder.toString().trim();
    }

    private String sanitizeApiError(RestClientResponseException exception) {
        String errorMessage = extractErrorMessage(exception.getResponseBodyAsString());
        int status = exception.getStatusCode().value();

        if (status == 401 || status == 403) {
            return "OpenAI rejected the request. Check the API key and try again.";
        }
        if (status == 429) {
            return "OpenAI rate limits prevented dashboard generation. Try again later.";
        }
        if (isModelError(errorMessage)) {
            return "The configured OpenAI model is unavailable for this API key. Check the configured model and try again.";
        }
        return "OpenAI dashboard generation could not be completed. Check the API key and try again later.";
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

    private record FunctionCall(String callId, String name, String arguments) {
    }
}
