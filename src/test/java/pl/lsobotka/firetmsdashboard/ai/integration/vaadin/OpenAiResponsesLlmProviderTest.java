package pl.lsobotka.firetmsdashboard.ai.integration.vaadin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.vaadin.flow.component.ai.common.ChatMessage;
import com.vaadin.flow.component.ai.provider.LLMProvider;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.lsobotka.firetmsdashboard.ai.integration.openai.AiOpenAiProperties;

class OpenAiResponsesLlmProviderTest {

    @Test
    void usesOutputTextForAssistantMessagesInConversationHistory() {
        OpenAiResponsesLlmProvider provider = new OpenAiResponsesLlmProvider(
                RestClient.builder().baseUrl("https://example.test").build(),
                new ObjectMapper(),
                new AiOpenAiProperties("https://example.test", "gpt-5.4-mini"),
                () -> "test-api-key");

        ArrayNode input = provider.buildInitialInput(
                request("Show totals", "You are a helpful assistant."),
                List.of(
                        new ChatMessage(ChatMessage.Role.USER, "Show totals", null, Instant.now()),
                        new ChatMessage(ChatMessage.Role.ASSISTANT, "I updated the chart.", null, Instant.now())));

        assertThat(input.get(0).path("content").get(0).path("type").asText()).isEqualTo("input_text");
        assertThat(input.get(1).path("content").get(0).path("type").asText()).isEqualTo("input_text");
        assertThat(input.get(2).path("content").get(0).path("type").asText()).isEqualTo("output_text");
    }

    private LLMProvider.LLMRequest request(String userMessage, String systemPrompt) {
        return new LLMProvider.LLMRequest() {
            @Override
            public String userMessage() {
                return userMessage;
            }

            @Override
            public List<com.vaadin.flow.component.ai.common.AIAttachment> attachments() {
                return List.of();
            }

            @Override
            public String systemPrompt() {
                return systemPrompt;
            }

            @Override
            public Object[] tools() {
                return new Object[0];
            }
        };
    }
}
