package rs.adsdev.ifconfig.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Flat response shape from {@code /json}, {@code /xml}, and per-item
 * {@code /batch} responses. Mirrors {@code FlatInfo} on the server side.
 *
 * <p>Envelope fields ({@code status}, {@code message}, {@code query}) describe
 * the outcome of the lookup. The rest are enrichment, hoisted to the top level,
 * so the wire is a single object with no nested wrappers. Any field that the
 * server omitted (e.g., enrichment for a private IP, or fields filtered out via
 * {@code ?fields=}) arrives as {@code null}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record IpInfo(
        String status,
        String message,
        String query,
        String continent,
        String continentCode,
        String country,
        String countryCode,
        String region,
        String regionName,
        String city,
        String district,
        String zip,
        Double lat,
        Double lon,
        String timezone,
        Integer offset,
        String currency,
        String isp,
        String org,
        String as,
        String asname,
        String reverse,
        Boolean mobile,
        Boolean proxy,
        Boolean hosting
) {
    /** Convenience predicate: did the server report a successful lookup? */
    public boolean isSuccess() {
        return "success".equals(status);
    }
}
