package rs.adsdev.ifconfig.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for the <a href="https://ifconfig.rs">ifconfig.rs</a> IP / geo lookup
 * service. Thin wrapper over the JDK {@link HttpClient}: every method maps 1:1
 * to a server endpoint.
 *
 * <p>Build an instance via {@link #builder()}:
 *
 * <pre>{@code
 * IfconfigClient client = IfconfigClient.builder()
 *         .baseUrl("https://ifconfig.rs")
 *         .apiKey("secret")
 *         .build();
 * IpInfo me = client.myIp();
 * }</pre>
 *
 * <p>The instance is thread-safe and reusable; reuse one per process rather
 * than creating per request.
 */
public final class IfconfigClient {

    private final @Nonnull URI baseUrl;
    private final @Nullable String apiKey;
    private final @Nonnull HttpClient http;
    private final @Nonnull Duration requestTimeout;
    private final @Nonnull String userAgent;
    private final @Nonnull ObjectMapper json;

    private IfconfigClient(final @Nonnull Builder builder) {
        this.baseUrl = URI.create(stripTrailingSlash(builder.baseUrl));
        this.apiKey = builder.apiKey;
        this.http = builder.httpClient != null ? builder.httpClient : HttpClient.newBuilder().connectTimeout(builder.connectTimeout).build();
        this.requestTimeout = builder.requestTimeout;
        this.userAgent = builder.userAgent;
        this.json = builder.objectMapper != null ? builder.objectMapper : new ObjectMapper();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** {@code GET /json} — returns geo info for the caller's own IP. */
    @Nonnull
    public IpInfo myIp() {
        return readJson(buildUri("/json", Map.of()), IP_INFO);
    }

    /** {@code GET /json?ip=...} — returns geo info for the supplied IPv4 / IPv6 literal. */
    @Nonnull
    public IpInfo lookup(final @Nonnull String ip) {
        requireNonBlank(ip, "ip");
        return readJson(buildUri("/json", Map.of("ip", ip)), IP_INFO);
    }

    /**
     * {@code GET /json?ip=...&fields=...} — restricts the response to the
     * requested fields. Use this when you only care about a few keys (e.g.,
     * just {@code country} on login) and want to skip serializing the rest.
     */
    @Nonnull
    public IpInfo lookup(final @Nonnull String ip, final @Nonnull Collection<Field> fields) {
        requireNonBlank(ip, "ip");
        // LinkedHashMap (not Map.of) so the query string ordering stays
        // ip=...&fields=... — Map.of randomizes iteration order, which would
        // make logs and test assertions non-deterministic.
        var params = new LinkedHashMap<String, String>(2);
        params.put("ip", ip);
        params.put("fields", Field.toQuery(fields));
        return readJson(buildUri("/json", params), IP_INFO);
    }

    /**
     * {@code GET /json?ip=...&fields=N} — like {@link #lookup(String, Collection)}
     * but uses the numeric bitmask encoding instead of the comma-separated wire
     * names. Build the mask with {@link Field#toBitmask(Collection)}.
     */
    @Nonnull
    public IpInfo lookup(final @Nonnull String ip, final int fieldBits) {
        requireNonBlank(ip, "ip");
        var params = new LinkedHashMap<String, String>(2);
        params.put("ip", ip);
        params.put("fields", Integer.toString(fieldBits));
        return readJson(buildUri("/json", params), IP_INFO);
    }

    private static final TypeReference<IpInfo> IP_INFO = new TypeReference<>() {};
    private static final TypeReference<List<IpInfo>> IP_INFO_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Integer>> FIELD_BITS = new TypeReference<>() {};

    /** {@code GET /plain} — returns the caller's own IP as plain text. */
    @Nonnull
    public String plain() {
        return readText(baseRequest(buildUri("/plain", Map.of()), "text/plain").GET().build());
    }

    /** {@code GET /xml?ip=...} — returns the raw XML payload for the supplied IP. */
    @Nonnull
    public String xml(final @Nonnull String ip) {
        requireNonBlank(ip, "ip");
        return readText(baseRequest(buildUri("/xml", Map.of("ip", ip)), "application/xml").GET().build());
    }

    /** The server caps a single {@code /batch} call at this many items. */
    public static final int MAX_BATCH_SIZE = 100;

    /**
     * {@code POST /batch} — looks up multiple addresses in a single round-trip.
     * Requires an API key. The server caps the batch at {@value #MAX_BATCH_SIZE}
     * items per call.
     */
    @Nonnull
    public List<IpInfo> batch(final @Nonnull List<BatchQuery> items) {
        if (apiKey == null) {
            throw new IllegalStateException("batch requires an apiKey; set one via IfconfigClient.builder().apiKey(...)");
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("batch items must not be empty");
        }
        if (items.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "batch size " + items.size() + " exceeds the server limit of " + MAX_BATCH_SIZE);
        }
        final byte[] body;
        try {
            body = json.writeValueAsBytes(items);
        } catch (final IOException ex) {
            throw new IfconfigException("Failed to serialize batch body", ex);
        }
        var req = baseRequest(buildUri("/batch", Map.of()), "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return readJson(req, IP_INFO_LIST);
    }

    /**
     * {@code GET /api/fields} — returns the canonical map of wire field name
     * → bit value (the single-bit mask {@code 1 << position}, e.g.
     * {@code country -> 32}). Useful for cross-checking {@link Field#bit()}
     * values against a particular server deployment.
     */
    @Nonnull
    public Map<String, Integer> fieldBits() {
        return readJson(buildUri("/api/fields", Map.of()), FIELD_BITS);
    }

    // ---------- internals ----------

    @Nonnull
    private URI buildUri(final @Nonnull String path, final @Nonnull Map<String, String> params) {
        var sb = new StringBuilder(baseUrl.toString()).append(path);
        if (!params.isEmpty()) {
            sb.append('?');
            var first = true;
            for (var e : params.entrySet()) {
                if (!first) {
                    sb.append('&');
                }
                first = false;
                sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            }
        }
        return URI.create(sb.toString());
    }

    @Nonnull
    private HttpRequest.Builder baseRequest(final @Nonnull URI uri, final @Nonnull String accept) {
        var builder = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", accept)
                .header("User-Agent", userAgent);
        if (apiKey != null) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder;
    }

    @Nonnull
    private <T> T readJson(final @Nonnull URI uri, final @Nonnull TypeReference<T> type) {
        return readJson(baseRequest(uri, "application/json").GET().build(), type);
    }

    @Nonnull
    private <T> T readJson(final @Nonnull HttpRequest req, final @Nonnull TypeReference<T> type) {
        var resp = send(req);
        try {
            return json.readValue(resp.body(), type);
        } catch (final IOException e) {
            // The body parsed cleanly at the HTTP layer (2xx) but not as JSON.
            // Carry the status and raw body so callers can see what came back.
            throw new IfconfigException(
                    "Failed to parse response body from " + req.uri(),
                    resp.statusCode(), resp.body(), e);
        }
    }

    @Nonnull
    private String readText(final @Nonnull HttpRequest req) {
        return send(req).body();
    }

    @Nonnull
    private HttpResponse<String> send(final @Nonnull HttpRequest req) {
        final HttpResponse<String> resp;
        try {
            // No explicit charset: ofString() honours the response Content-Type
            // charset and falls back to UTF-8 when none is declared.
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (final IOException e) {
            throw new IfconfigException("Transport error calling " + req.uri(), e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IfconfigException("Interrupted calling " + req.uri(), e);
        }
        if (resp.statusCode() / 100 != 2) {
            throw new IfconfigException(
                    "ifconfig returned HTTP " + resp.statusCode() + " for " + req.uri(),
                    resp.statusCode(),
                    resp.body());
        }
        return resp;
    }

    @Nonnull
    private static String stripTrailingSlash(final @Nonnull String s) {
        var end = s.length();
        while (end > 0 && s.charAt(end - 1) == '/') {
            end--;
        }
        return s.substring(0, end);
    }

    private static void requireNonBlank(final @Nullable String value, final @Nonnull String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
    }

    // ---------- builder ----------

    public static final class Builder {
        private String baseUrl = "https://ifconfig.rs";
        private @Nullable String apiKey;
        private @Nullable HttpClient httpClient;
        private @Nullable ObjectMapper objectMapper;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration requestTimeout = Duration.ofSeconds(10);
        private String userAgent = "ifconfig-java/1.0";

        private Builder() {}

        /** Base URL of the ifconfig service. Defaults to {@code https://ifconfig.rs}. */
        public Builder baseUrl(final @Nonnull String url) {
            this.baseUrl = url;
            return this;
        }

        /**
         * API key sent as {@code Authorization: Bearer <key>}. Required for
         * {@link #batch(List) batch} lookups; optional for the rate-limited
         * public endpoints.
         */
        public Builder apiKey(final @Nullable String key) {
            this.apiKey = key;
            return this;
        }

        /** Use a caller-supplied {@link HttpClient} instead of the default. */
        public Builder httpClient(final @Nonnull HttpClient client) {
            this.httpClient = client;
            return this;
        }

        /** Use a caller-supplied {@link ObjectMapper}. */
        public Builder objectMapper(final @Nonnull ObjectMapper mapper) {
            this.objectMapper = mapper;
            return this;
        }

        /** TCP connect timeout for the default {@link HttpClient}. */
        public Builder connectTimeout(final @Nonnull Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        /** Per-request timeout (read and response). */
        public Builder requestTimeout(final @Nonnull Duration timeout) {
            this.requestTimeout = timeout;
            return this;
        }

        /** Overrides the {@code User-Agent} header on every request. */
        public Builder userAgent(final @Nonnull String ua) {
            this.userAgent = ua;
            return this;
        }

        public IfconfigClient build() {
            requireNonBlank(baseUrl, "baseUrl");
            return new IfconfigClient(this);
        }
    }
}
