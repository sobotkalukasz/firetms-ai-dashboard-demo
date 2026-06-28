package pl.lsobotka.firetmsdashboard.firetms.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.lsobotka.firetmsdashboard.firetms.FireTmsProperties;

class FireTmsClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void buildsUriFromPathAndQueryParams() {
        FireTmsClient client = new FireTmsClient(
                RestClient.builder().build(),
                new ObjectMapper(),
                new FireTmsProperties("https://app.firetms.com/api"));

        URI uri = client.buildUri(new FireTmsRequest(
                "/invoices/sales/issued",
                Map.of(
                        "dateOfIssueFrom", LocalDate.of(2026, 6, 1),
                        "dateOfIssueTo", LocalDate.of(2026, 6, 30))));

        assertThat(uri.toString()).startsWith("https://app.firetms.com/api/invoices/sales/issued?");
        assertThat(uri.toString()).contains("dateOfIssueFrom=2026-06-01");
        assertThat(uri.toString()).contains("dateOfIssueTo=2026-06-30");
    }

    @Test
    void fetchesAllPagesAndMergesItemsIntoSinglePayload() throws Exception {
        CopyOnWriteArrayList<String> requests = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new java.net.InetSocketAddress(0), 0);
        server.createContext("/api/invoices/sales/issued", exchange -> {
            requests.add(exchange.getRequestURI().getQuery());
            String page = readQueryParam(exchange, "page");
            String responseBody = "1".equals(page)
                    ? """
                    {
                      "items": [{"documentNumber":"3"}],
                      "maxPageNumber": 1,
                      "paging": {
                        "firstItemIndex": 2,
                        "pageNumber": 1,
                        "pageSize": 2
                      },
                      "totalItems": 3
                    }
                    """
                    : """
                    {
                      "items": [{"documentNumber":"1"},{"documentNumber":"2"}],
                      "maxPageNumber": 1,
                      "paging": {
                        "firstItemIndex": 0,
                        "pageNumber": 0,
                        "pageSize": 2
                      },
                      "totalItems": 3
                    }
                    """;
            writeJson(exchange, responseBody);
        });
        server.start();

        String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/api";
        FireTmsClient client = new FireTmsClient(
                RestClient.builder().build(),
                new ObjectMapper(),
                new FireTmsProperties(baseUrl));

        FireTmsResponse response = client.get("secret", new FireTmsRequest(
                "/invoices/sales/issued",
                Map.of(
                        "dateOfIssueFrom", LocalDate.of(2026, 6, 1),
                        "dateOfIssueTo", LocalDate.of(2026, 6, 30))));

        JsonNode payload = response.payload();
        assertThat(payload.path("totalItems").intValue()).isEqualTo(3);
        assertThat(payload.path("items")).hasSize(3);
        assertThat(payload.path("items").get(0).path("documentNumber").asText()).isEqualTo("1");
        assertThat(payload.path("items").get(2).path("documentNumber").asText()).isEqualTo("3");
        assertThat(response.rawJson()).contains("\"documentNumber\":\"3\"");
        assertThat(requests).hasSize(2);
        assertThat(requests.getFirst()).contains("dateOfIssueFrom=2026-06-01");
        assertThat(requests.getFirst()).doesNotContain("page=");
        assertThat(requests.get(1)).contains("page=1");
    }

    private String readQueryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null || query.isBlank()) {
            return null;
        }

        for (String entry : query.split("&")) {
            String[] parts = entry.split("=", 2);
            String key = java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            if (name.equals(key)) {
                return parts.length > 1 ? java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            }
        }
        return null;
    }

    private void writeJson(HttpExchange exchange, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
