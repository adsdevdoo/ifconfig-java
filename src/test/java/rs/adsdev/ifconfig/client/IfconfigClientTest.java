package rs.adsdev.ifconfig.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
@DisplayName("IfconfigClient: HTTP wire contract and response parsing")
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
    @DisplayName("myIp() hits GET /json, sends bearer token, and parses the flat envelope")
    void myIpParsesFlatJsonResponse() {
        nextResponses.push(json("""
                {"status":"success","query":"203.0.113.5","country":"Germany","countryCode":"DE","city":"Berlin"}
                """));

        var info = client().myIp();
        var req = seenRequests.pop();

        assertEquals("GET", req.method);
        assertEquals("/json", req.path);
        assertEquals("Bearer test-token", req.headers.get("Authorization"));
        assertTrue(info.isSuccess());
        assertEquals("DE", info.countryCode());
        assertEquals("Berlin", info.city());
        assertNull(info.message());
    }

    @Test
    @DisplayName("lookup(ip) sends only ?ip= and parses the response")
    void lookupWithoutFieldsSendsOnlyIpQuery() {
        nextResponses.push(json("""
                {"status":"success","query":"1.2.3.4","countryCode":"US"}
                """));

        var info = client().lookup("1.2.3.4");
        var req = seenRequests.pop();

        assertEquals("/json", req.path);
        assertEquals("ip=1.2.3.4", req.query);
        assertEquals("US", info.countryCode());
    }

    @Test
    @DisplayName("lookup(ip, fields) sends ?ip=&fields= with deterministic order and wire-name CSV")
    void lookupSendsIpAndFieldsQuery() {
        nextResponses.push(json("""
                {"status":"success","country":"Russia","countryCode":"RU"}
                """));

        var info = client().lookup("8.8.8.8", EnumSet.of(Field.COUNTRY, Field.COUNTRY_CODE));
        var req = seenRequests.pop();

        assertEquals("/json", req.path);
        assertEquals("ip=8.8.8.8&fields=country%2CcountryCode", req.query);
        assertEquals("RU", info.countryCode());
    }

    @Test
    @DisplayName("plain() hits GET /plain and returns the raw text body without parsing")
    void plainReturnsRawBody() {
        nextResponses.push(new CannedResponse(200, "text/plain", "203.0.113.5"));

        var body = client().plain();
        var req = seenRequests.pop();

        assertEquals("/plain", req.path);
        assertEquals("203.0.113.5", body);
    }

    @Test
    @DisplayName("xml(ip) hits GET /xml?ip= and returns the raw XML body for caller-side parsing")
    void xmlReturnsRawBodyAndSendsIpQuery() {
        var payload = "<Info><status>success</status><country>Germany</country></Info>";
        nextResponses.push(new CannedResponse(200, "application/xml", payload));

        var body = client().xml("203.0.113.5");
        var req = seenRequests.pop();

        assertEquals("GET", req.method);
        assertEquals("/xml", req.path);
        assertEquals("ip=203.0.113.5", req.query);
        assertEquals(payload, body);
    }

    @Test
    @DisplayName("batch() POSTs JSON array to /batch and parses the array response")
    void batchPostsJsonArrayAndParsesArrayResponse() {
        nextResponses.push(json("""
                [
                  {"status":"success","query":"1.1.1.1","country":"Australia"},
                  {"status":"success","query":"8.8.8.8","country":"United States"}
                ]
                """));

        var out = client().batch(List.of(
                new BatchQuery("1.1.1.1"),
                new BatchQuery("8.8.8.8", "country")));
        var req = seenRequests.pop();

        assertEquals("POST", req.method);
        assertEquals("/batch", req.path);
        assertEquals("application/json", req.headers.get("Content-Type"));
        assertTrue(req.body.contains("\"query\":\"1.1.1.1\""));
        assertTrue(req.body.contains("\"fields\":\"country\""));
        assertEquals(2, out.size());
        assertEquals("Australia", out.getFirst().country());
    }

    @Test
    @DisplayName("fieldBits() parses GET /api/fields into a name -> bit-position integer map")
    void fieldBitsParsesIntegerMap() {
        nextResponses.push(json("""
                {"status":1,"message":2,"query":4,"country":32,"hosting":16777216}
                """));

        var bits = client().fieldBits();

        assertEquals(1, bits.get("status"));
        assertEquals(32, bits.get("country"));
        assertEquals(16777216, bits.get("hosting"));
    }

    @Test
    @DisplayName("Non-2xx response raises IfconfigException carrying status code and raw body")
    void nonSuccessfulStatusRaisesIfconfigException() {
        nextResponses.push(new CannedResponse(429, "application/json",
                "{\"error\":\"Too Many Requests\"}"));

        var ex = assertThrows(IfconfigException.class, () -> client().myIp());
        assertEquals(429, ex.statusCode());
        var body = ex.body();
        assertNotNull(body);
        assertTrue(body.contains("Too Many Requests"));
    }

    @Test
    @DisplayName("Builder options (userAgent, httpClient, objectMapper, timeouts) propagate to requests; no apiKey => no Authorization header")
    void builderOptionsAreAppliedToRequests() {
        nextResponses.push(json("""
                {"status":"success","country":"NL"}
                """));

        var customMapper = new ObjectMapper();
        var customHttp = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        var custom = IfconfigClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/")
                .httpClient(customHttp)
                .objectMapper(customMapper)
                .connectTimeout(Duration.ofSeconds(1))
                .requestTimeout(Duration.ofSeconds(3))
                .userAgent("custom-agent/9.9")
                .build();

        var info = custom.myIp();
        var req = seenRequests.pop();

        // baseUrl with trailing slash must not produce //json
        assertEquals("/json", req.path);
        assertEquals("custom-agent/9.9", req.headers.get("User-Agent"));
        // No apiKey() call → no Authorization header
        assertNull(req.headers.get("Authorization"));
        assertEquals("NL", info.country());
    }

    @Test
    @DisplayName("Field.toQuery() emits wire-name CSV and Field.toBitmask() ORs bit positions, both matching server encoding")
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
            var reqBody = exchange.getRequestBody().readAllBytes();
            // JDK's com.sun.net.httpserver.Headers normalizes keys to
            // first-letter-uppercase (e.g., "User-agent"); use a
            // case-insensitive map so tests can look up by canonical case.
            var headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
            exchange.getRequestHeaders().forEach((k, v) -> {
                if (!v.isEmpty()) {
                    headers.put(k, v.getFirst());
                }
            });
            seenRequests.push(new CapturedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawQuery(),
                    headers,
                    new String(reqBody, StandardCharsets.UTF_8)));

            var resp = nextResponses.isEmpty()
                    ? new CannedResponse(500, "text/plain", "no canned response")
                    : nextResponses.pop();
            var body = resp.body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", resp.contentType);
            exchange.sendResponseHeaders(resp.status, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }
}
