package pl.lsobotka.firetmsdashboard.ai;

public class OpenAiIntentClassificationException extends RuntimeException {

    public OpenAiIntentClassificationException(String message) {
        super(message);
    }

    public OpenAiIntentClassificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
