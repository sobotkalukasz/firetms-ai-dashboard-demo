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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.lsobotka.firetmsdashboard.ai.application.AiQueryGenerationResult;
import pl.lsobotka.firetmsdashboard.ai.integration.openai.AiOpenAiProperties;
import pl.lsobotka.firetmsdashboard.ai.integration.openai.OpenAiSqlGenerationException;
import pl.lsobotka.firetmsdashboard.ai.integration.openai.OpenAiVisualizationGenerator;
import pl.lsobotka.firetmsdashboard.ai.model.AiVisualizationSpec.VisualizationType;
import pl.lsobotka.firetmsdashboard.ai.query.SqlSafetyValidator;

class OpenAiVisualizationGeneratorTest {

    private final SqlSafetyValidator sqlSafetyValidator = new SqlSafetyValidator();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void generatesSqlAndVisualizationUsingOpenAiResponsesApi() throws Exception {
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
                              "text": "{\\"sql\\":\\"select invoice_number, gross_amount from ai_sales_invoice_view order by gross_amount desc limit 25\\",\\"visualization\\":\\"TABLE\\",\\"title\\":\\"Top invoices\\",\\"xColumn\\":null,\\"yColumn\\":null,\\"seriesColumn\\":null,\\"explanation\\":\\"Lists invoices with the largest gross amounts.\\"}"
                            }
                          ]
                        }
                      ]
                    }
                    """);
        });
        server.start();

        OpenAiVisualizationGenerator generator = generatorForServer("gpt-5.5-mini");

        AiQueryGenerationResult result = generator.generate("test-api-key", "Show the biggest invoices");

        assertThat(result.sql()).contains("from ai_sales_invoice_view");
        assertThat(result.title()).isEqualTo("Top invoices");
        assertThat(result.explanation()).contains("largest gross amounts");
        assertThat(result.visualizationSpec().visualization()).isEqualTo(VisualizationType.TABLE);
        assertThat(authorizationHeader.get()).isEqualTo("Bearer test-api-key");
        assertThat(requestBody.get()).contains("Show the biggest invoices");
        assertThat(requestBody.get()).contains("ai_sales_invoice_view");
        assertThat(requestBody.get()).contains("return strict JSON only");
        assertThat(requestBody.get()).contains("always add a LIMIT");
        assertThat(requestBody.get()).contains("\"visualization\"");
        assertThat(requestBody.get()).contains("never query raw_json");
        assertThat(requestBody.get()).doesNotContain("{\"private\":true}");
    }

    @Test
    void retriesWhenOpenAiReturnsSqlForForbiddenTable() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<String> secondRequestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> {
            int attempt = requestCount.incrementAndGet();
            String requestBody = readBody(exchange);
            if (attempt == 2) {
                secondRequestBody.set(requestBody);
            }
            if (attempt == 1) {
                writeJson(exchange, 200, """
                        {
                          "output": [
                            {
                              "type": "message",
                              "content": [
                                {
                                  "type": "output_text",
                                  "text": "{\\"sql\\":\\"select invoice_number from sales_invoice limit 10\\",\\"visualization\\":\\"TABLE\\",\\"title\\":\\"Bad query\\",\\"xColumn\\":null,\\"yColumn\\":null,\\"seriesColumn\\":null,\\"explanation\\":\\"Uses the wrong table.\\"}"
                                }
                              ]
                            }
                          ]
                        }
                        """);
                return;
            }
            writeJson(exchange, 200, """
                    {
                      "output": [
                        {
                          "type": "message",
                          "content": [
                            {
                              "type": "output_text",
                              "text": "{\\"sql\\":\\"select invoice_number from ai_sales_invoice_view order by issue_date desc limit 10\\",\\"visualization\\":\\"TABLE\\",\\"title\\":\\"Recent invoices\\",\\"xColumn\\":null,\\"yColumn\\":null,\\"seriesColumn\\":null,\\"explanation\\":\\"Lists invoices from the restricted AI view.\\"}"
                            }
                          ]
                        }
                      ]
                    }
                    """);
        });
        server.start();

        OpenAiVisualizationGenerator generator = generatorForServer("gpt-5.5-mini");

        AiQueryGenerationResult result = generator.generate("test-api-key", "Show recent invoices");

        assertThat(requestCount.get()).isEqualTo(2);
        assertThat(result.sql()).contains("from ai_sales_invoice_view");
        assertThat(secondRequestBody.get()).contains("The previous SQL was invalid and must be corrected.");
        assertThat(secondRequestBody.get()).contains("select invoice_number from sales_invoice limit 10");
        assertThat(secondRequestBody.get()).contains("Generated SQL references a forbidden table or column.");
    }

    @Test
    void correctsSqlUsingExplicitDatabaseFeedback() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> {
            requestBody.set(readBody(exchange));
            writeJson(exchange, 200, """
                    {
                      "output": [
                        {
                          "type": "message",
                          "content": [
                            {
                              "type": "output_text",
                              "text": "{\\"sql\\":\\"select formatdatetime(issue_date, 'yyyy-MM') as month_value, sum(gross_amount) as gross_sales from ai_sales_invoice_view group by formatdatetime(issue_date, 'yyyy-MM') order by month_value limit 100\\",\\"visualization\\":\\"LINE\\",\\"title\\":\\"Monthly gross sales\\",\\"xColumn\\":\\"month_value\\",\\"yColumn\\":\\"gross_sales\\",\\"seriesColumn\\":null,\\"explanation\\":\\"Shows gross sales grouped by issue month.\\"}"
                            }
                          ]
                        }
                      ]
                    }
                    """);
        });
        server.start();

        OpenAiVisualizationGenerator generator = generatorForServer("gpt-5.5-mini");

        AiQueryGenerationResult result = generator.correct(
                "test-api-key",
                "show monthly gross sales",
                "select formatdatetime(issue_date, 'yyyy-MM') as month, sum(gross_amount) as gross_sales from ai_sales_invoice_view group by formatdatetime(issue_date, 'yyyy-MM') order by month limit 100",
                "Syntax error in SQL statement; expected identifier");

        assertThat(result.sql()).contains("as month_value");
        assertThat(result.visualizationSpec().visualization()).isEqualTo(VisualizationType.LINE);
        assertThat(requestBody.get()).contains("The previous SQL was invalid and must be corrected.");
        assertThat(requestBody.get()).contains("as month, sum(gross_amount)");
        assertThat(requestBody.get()).contains("Syntax error in SQL statement; expected identifier");
    }

    @Test
    void rejectsUnexpectedResponseShape() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> writeJson(exchange, 200, """
                {
                  "id": "resp_123",
                  "status": "completed"
                }
                """));
        server.start();

        OpenAiVisualizationGenerator generator = generatorForServer("gpt-5.5-mini");

        assertThatThrownBy(() -> generator.generate("test-api-key", "Show gross sales"))
                .isInstanceOf(OpenAiSqlGenerationException.class)
                .hasMessage("OpenAI returned an unexpected response shape.");
    }

    @Test
    void rejectsMalformedDashboardJson() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> writeJson(exchange, 200, """
                {
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "{\\"title\\":\\"Missing SQL\\",\\"visualization\\":\\"TABLE\\",\\"xColumn\\":null,\\"yColumn\\":null,\\"seriesColumn\\":null,\\"explanation\\":\\"bad\\"}"
                        }
                      ]
                    }
                  ]
                }
                """));
        server.start();

        OpenAiVisualizationGenerator generator = generatorForServer("gpt-5.5-mini");

        assertThatThrownBy(() -> generator.generate("test-api-key", "Show gross sales"))
                .isInstanceOf(OpenAiSqlGenerationException.class)
                .hasMessage("OpenAI returned dashboard output without SQL.");
    }

    @Test
    void rejectsUnsupportedVisualizationType() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> writeJson(exchange, 200, """
                {
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "{\\"sql\\":\\"select invoice_number from ai_sales_invoice_view limit 10\\",\\"visualization\\":\\"SCATTER\\",\\"title\\":\\"Bad visualization\\",\\"xColumn\\":\\"invoice_number\\",\\"yColumn\\":null,\\"seriesColumn\\":null,\\"explanation\\":\\"unsupported\\"}"
                        }
                      ]
                    }
                  ]
                }
                """));
        server.start();

        OpenAiVisualizationGenerator generator = generatorForServer("gpt-5.5-mini");

        assertThatThrownBy(() -> generator.generate("test-api-key", "Show invoices"))
                .isInstanceOf(OpenAiSqlGenerationException.class)
                .hasMessage("OpenAI returned an unsupported visualization type.");
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

        OpenAiVisualizationGenerator generator = generatorForServer("gpt-5.5-mini");

        assertThatThrownBy(() -> generator.generate("test-api-key", "Show gross sales"))
                .isInstanceOf(OpenAiSqlGenerationException.class)
                .hasMessage("The configured OpenAI model is unavailable for this API key. Check the configured model and try again.");
    }

    private OpenAiVisualizationGenerator generatorForServer(String model) {
        String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/v1";
        return new OpenAiVisualizationGenerator(
                RestClient.builder().baseUrl(baseUrl).build(),
                new ObjectMapper(),
                new AiOpenAiProperties(baseUrl, model),
                sqlSafetyValidator);
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
