package pl.lsobotka.firetmsdashboard.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OpenAiIntentClassifierTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void classifiesPromptUsingOpenAiResponsesApi() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(readBody(exchange));
            writeJson(exchange, 200, """
                    {
                      "output": [
                        {
                          "type": "message",
                          "content": [
                            {
                              "type": "output_text",
                              "text": "{\\"intent\\":\\"TOP_CONTRACTORS_BY_GROSS_AMOUNT\\",\\"limit\\":10,\\"reason\\":\\"The prompt asks for top contractors by gross amount.\\"}"
                            }
                          ]
                        }
                      ]
                    }
                    """);
        });
        server.start();

        OpenAiIntentClassifier classifier = classifierForServer("gpt-5.5-mini");

        AiIntentClassificationResult result = classifier.classify("test-api-key", "Show top 10 contractors by gross amount");

        assertThat(result.intent()).isEqualTo(AiDashboardIntent.TOP_CONTRACTORS_BY_GROSS_AMOUNT);
        assertThat(result.limit()).isEqualTo(10);
        assertThat(result.reason()).contains("top contractors");
        assertThat(authorizationHeader.get()).isEqualTo("Bearer test-api-key");
        assertThat(requestBody.get()).contains("Show top 10 contractors by gross amount");
        assertThat(requestBody.get()).contains("GROSS_SALES_BY_MONTH");
        assertThat(requestBody.get()).contains("TOP_CONTRACTORS_BY_GROSS_AMOUNT");
        assertThat(requestBody.get()).contains("\"json_schema\"");
        assertThat(requestBody.get()).doesNotContain("raw_json");
        assertThat(requestBody.get()).doesNotContain("ai_sales_invoice_view");
    }

    @Test
    void returnsUnknownIntentWhenOpenAiCannotMatchPrompt() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> writeJson(exchange, 200, """
                {
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "{\\"intent\\":\\"UNKNOWN\\",\\"limit\\":10,\\"reason\\":\\"The request does not match the supported dashboard intents.\\"}"
                        }
                      ]
                    }
                  ]
                }
                """));
        server.start();

        OpenAiIntentClassifier classifier = classifierForServer("gpt-5.5-mini");

        AiIntentClassificationResult result = classifier.classify("test-api-key", "Find payment anomalies");

        assertThat(result.intent()).isEqualTo(AiDashboardIntent.UNKNOWN);
        assertThat(result.reason()).contains("does not match");
    }

    @Test
    void rejectsMalformedClassificationResponsesSafely() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> writeJson(exchange, 200, """
                {
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "{\\"intent\\":\\"NOT_A_REAL_INTENT\\",\\"limit\\":10,\\"reason\\":\\"bad\\"}"
                        }
                      ]
                    }
                  ]
                }
                """));
        server.start();

        OpenAiIntentClassifier classifier = classifierForServer("gpt-5.5-mini");

        assertThatThrownBy(() -> classifier.classify("test-api-key", "Show something unsupported"))
                .isInstanceOf(OpenAiIntentClassificationException.class)
                .hasMessage("OpenAI returned an unsupported dashboard intent.");
    }

    @Test
    void rejectsInvalidOpenAiResponseShapeSafely() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> writeJson(exchange, 200, """
                {
                  "id": "resp_123",
                  "status": "completed"
                }
                """));
        server.start();

        OpenAiIntentClassifier classifier = classifierForServer("gpt-5.5-mini");

        assertThatThrownBy(() -> classifier.classify("test-api-key", "Show gross sales by month"))
                .isInstanceOf(OpenAiIntentClassificationException.class)
                .hasMessage("OpenAI returned an unexpected response shape.");
    }

    @Test
    void sanitizesModelErrors() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> writeJson(exchange, 400, """
                {
                  "error": {
                    "message": "The model `gpt-5.5-mini` does not exist or you do not have access to it."
                  }
                }
                """));
        server.start();

        OpenAiIntentClassifier classifier = classifierForServer("gpt-5.5-mini");

        assertThatThrownBy(() -> classifier.classify("test-api-key", "Show gross sales by month"))
                .isInstanceOf(OpenAiIntentClassificationException.class)
                .hasMessage("The configured OpenAI model is unavailable for this API key. Check the configured model and try again.");
    }

    private OpenAiIntentClassifier classifierForServer(String model) {
        String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/v1";
        return new OpenAiIntentClassifier(
                RestClient.builder().baseUrl(baseUrl).build(),
                new ObjectMapper(),
                new AiOpenAiProperties(baseUrl, model));
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream inputStream = exchange.getRequestBody()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
