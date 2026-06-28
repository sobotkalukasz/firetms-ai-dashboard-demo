package pl.lsobotka.firetmsdashboard.ai.integration.openai;

public class OpenAiSqlGenerationException extends RuntimeException {

    public OpenAiSqlGenerationException(String message) {
        super(message);
    }

    public OpenAiSqlGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
