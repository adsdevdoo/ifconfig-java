package rs.adsdev.ifconfig.client;

import jakarta.annotation.Nullable;

import java.io.Serial;

/**
 * Thrown when the ifconfig service returns a non-2xx response, or when the
 * request fails at the transport / serialization layer. For non-2xx responses
 * (and for 2xx responses whose body could not be parsed), {@link #statusCode()}
 * carries the HTTP status and {@link #body()} carries the raw response body
 * (typically a JSON error envelope). For transport errors, status is
 * {@code 0} and body is {@code null}.
 */
public final class IfconfigException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int statusCode;
    private final @Nullable String body;

    public IfconfigException(final String message, final int statusCode, final @Nullable String body) {
        super(message);
        this.statusCode = statusCode;
        this.body = body;
    }

    public IfconfigException(final String message, final int statusCode, final @Nullable String body, final Throwable cause) {
        super(message, cause);
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
