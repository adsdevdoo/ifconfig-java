package rs.adsdev.ifconfig.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives {@link IfconfigClient} against a JDK in-process HTTP server so the
 * tests run with zero external dependencies. Each test pre-loads a canned
 * response on the server and then asserts the client sent the expected
 * method/path/headers and parsed the response correctly.
 */
class IfconfigClientTest {

    private HttpServer server;
    private final Deque<CannedResponse> nextResponses = new ArrayDeque<>();
    private final Deque<CapturedRequest> seenRequests = new ArrayDeque<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new RecordingHandler());
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private IfconfigClient client() {
        return IfconfigClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .apiKey("test-token")
                .build();
    }

    @Test
    void myIpParsesFlatJsonResponse() {
        nextResponses.push(json("""
                {"status":"success","query":"203.0.113.5","country":"Germany","countryCode":"DE","city":"Berlin"}
                """));

        final IpInfo info = client().myIp();
        final CapturedRequest req = seenRequests.pop();

        assertEquals("GET", req.method);
        assertEquals("/json", req.path);
        assertEquals("Bearer test-token", req.headers.get("Authorization"));
        assertTrue(info.isSuccess());
        assertEquals("DE", info.countryCode());
        assertEquals("Berlin", info.city());
        assertNull(info.message());
    }

    @Test
    void lookupWithoutFieldsSendsOnlyIpQuery() {
        nextResponses.push(json("""
                {"status":"success","query":"1.2.3.4","countryCode":"US"}
                """));

        final IpInfo info = client().lookup("1.2.3.4");
        final CapturedRequest req = seenRequests.pop();

        assertEquals("/json", req.path);
        assertEquals("ip=1.2.3.4", req.query);
        assertEquals("US", info.countryCode());
    }

    @Test
    void lookupSendsIpAndFieldsQuery() {
        nextResponses.push(json("""
                {"status":"success","country":"Russia","countryCode":"RU"}
                """));

        final IpInfo info = client().lookup("8.8.8.8", EnumSet.of(Field.COUNTRY, Field.COUNTRY_CODE));
        final CapturedRequest req = seenRequests.pop();

        assertEquals("/json", req.path);
        assertEquals("ip=8.8.8.8&fields=country%2CcountryCode", req.query);
        assertEquals("RU", info.countryCode());
    }

    @Test
    void plainReturnsRawBody() {
        nextResponses.push(new CannedResponse(200, "text/plain", "203.0.113.5"));

        final String body = client().plain();
        final CapturedRequest req = seenRequests.pop();

        assertEquals("/plain", req.path);
        assertEquals("203.0.113.5", body);
    }

    @Test
    void xmlReturnsRawBodyAndSendsIpQuery() {
        final String payload = "<Info><status>success</status><country>Germany</country></Info>";
        nextResponses.push(new CannedResponse(200, "application/xml", payload));

        final String body = client().xml("203.0.113.5");
        final CapturedRequest req = seenRequests.pop();

        assertEquals("GET", req.method);
        assertEquals("/xml", req.path);
        assertEquals("ip=203.0.113.5", req.query);
        assertEquals(payload, body);
    }

    @Test
    void batchPostsJsonArrayAndParsesArrayResponse() {
        nextResponses.push(json("""
                [
                  {"status":"success","query":"1.1.1.1","country":"Australia"},
                  {"status":"success","query":"8.8.8.8","country":"United States"}
                ]
                """));

        final List<IpInfo> out = client().batch(List.of(
                new BatchQuery("1.1.1.1"),
                new BatchQuery("8.8.8.8", "country")));
        final CapturedRequest req = seenRequests.pop();

        assertEquals("POST", req.method);
        assertEquals("/batch", req.path);
        assertEquals("application/json", req.headers.get("Content-Type"));
        assertTrue(req.body.contains("\"query\":\"1.1.1.1\""));
        assertTrue(req.body.contains("\"fields\":\"country\""));
        assertEquals(2, out.size());
        assertEquals("Australia", out.getFirst().country());
    }

    @Test
    void fieldBitsParsesIntegerMap() {
        nextResponses.push(json("""
                {"status":1,"message":2,"query":4,"country":32,"hosting":16777216}
                """));

        final Map<String, Integer> bits = client().fieldBits();

        assertEquals(1, bits.get("status"));
        assertEquals(32, bits.get("country"));
        assertEquals(16777216, bits.get("hosting"));
    }

    @Test
    void nonSuccessfulStatusRaisesIfconfigException() {
        nextResponses.push(new CannedResponse(429, "application/json",
                "{\"error\":\"Too Many Requests\"}"));

        final IfconfigException ex = assertThrows(IfconfigException.class, () -> client().myIp());
        assertEquals(429, ex.statusCode());
        final String body = ex.body();
        assertNotNull(body);
        assertTrue(body.contains("Too Many Requests"));
    }

    @Test
    void builderOptionsAreAppliedToRequests() {
        nextResponses.push(json("""
                {"status":"success","country":"NL"}
                """));

        final ObjectMapper customMapper = new ObjectMapper();
        final HttpClient customHttp = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        final IfconfigClient custom = IfconfigClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/")
                .httpClient(customHttp)
                .objectMapper(customMapper)
                .connectTimeout(Duration.ofSeconds(1))
                .requestTimeout(Duration.ofSeconds(3))
                .userAgent("custom-agent/9.9")
                .build();

        final IpInfo info = custom.myIp();
        final CapturedRequest req = seenRequests.pop();

        // baseUrl with trailing slash must not produce //json
        assertEquals("/json", req.path);
        assertEquals("custom-agent/9.9", req.headers.get("User-Agent"));
        // No apiKey() call → no Authorization header
        assertNull(req.headers.get("Authorization"));
        assertEquals("NL", info.country());
    }

    @Test
    void fieldToQueryAndBitmaskMatchServerEncoding() {
        // Mirrors the FlatField bits pinned on the server: country=1<<5, city=1<<9.
        assertEquals("country,city", Field.toQuery(List.of(Field.COUNTRY, Field.CITY)));
        assertEquals((1 << 5) | (1 << 9), Field.toBitmask(List.of(Field.COUNTRY, Field.CITY)));
    }

    // ---------- test harness ----------

    private static CannedResponse json(final String body) {
        return new CannedResponse(200, "application/json", body.trim());
    }

    private record CannedResponse(int status, String contentType, String body) {}

    private record CapturedRequest(String method, String path, String query,
                                   Map<String, String> headers, String body) {}

    private final class RecordingHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            final byte[] reqBody = exchange.getRequestBody().readAllBytes();
            final Map<String, String> hdrs = new java.util.HashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> {
                if (!v.isEmpty()) {
                    hdrs.put(k, v.getFirst());
                }
            });
            seenRequests.push(new CapturedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawQuery(),
                    hdrs,
                    new String(reqBody, StandardCharsets.UTF_8)));

            final CannedResponse resp = nextResponses.isEmpty()
                    ? new CannedResponse(500, "text/plain", "no canned response")
                    : nextResponses.pop();
            final byte[] body = resp.body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", resp.contentType);
            exchange.sendResponseHeaders(resp.status, body.length);
            try (final OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }
}
