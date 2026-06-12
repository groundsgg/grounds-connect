package gg.grounds.connect.api;

/** Raised on a non-2xx forge API response; carries the HTTP status for retry decisions. */
public class ForgeApiException extends RuntimeException {
    private final int statusCode;

    public ForgeApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }

    public boolean isUnauthorized() {
        return statusCode == 401;
    }
}
