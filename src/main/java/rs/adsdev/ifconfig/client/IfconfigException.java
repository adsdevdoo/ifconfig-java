package rs.adsdev.ifconfig.client;

import jakarta.annotation.Nullable;

/**
 * Thrown when the ifconfig service returns a non-2xx response, or when the
 * request fails at the transport / serialization layer. For non-2xx responses,
 * {@link #statusCode()} carries the HTTP status and {@link #body()} carries the
 * raw response body (typically a JSON error envelope). For transport errors,
 * status is {@code 0} and body is {@code null}.
 */
public class IfconfigException extends RuntimeException {

    private final int statusCode;
    private final @Nullable String body;

    public IfconfigException(final String message, final int statusCode, final @Nullable String body) {
        super(message);
        this.statusCode = statusCode;
        this.body = body;
    }

    public IfconfigException(final String message, final Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.body = null;
    }

    public int statusCode() {
        return statusCode;
    }

    @Nullable
    public String body() {
        return body;
    }
}
