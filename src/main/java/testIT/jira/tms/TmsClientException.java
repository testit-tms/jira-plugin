package testIT.jira.tms;

public class TmsClientException extends Exception {
    private final int statusCode;

    public TmsClientException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
