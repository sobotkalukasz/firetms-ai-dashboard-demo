package pl.lsobotka.firetmsdashboard.ai.integration.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "ai.openai")
public record AiOpenAiProperties(String baseUrl, String model) {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "gpt-5.5-mini";

    public AiOpenAiProperties {
        baseUrl = StringUtils.hasText(baseUrl) ? baseUrl : DEFAULT_BASE_URL;
        model = StringUtils.hasText(model) ? model : DEFAULT_MODEL;
    }
}
