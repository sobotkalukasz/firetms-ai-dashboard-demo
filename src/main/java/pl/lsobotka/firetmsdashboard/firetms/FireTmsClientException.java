package pl.lsobotka.firetmsdashboard.firetms;

public class FireTmsClientException extends RuntimeException {

    public FireTmsClientException(String message) {
        super(message);
    }

    public FireTmsClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
