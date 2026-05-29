package rs.adsdev.ifconfig.client;

import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * The set of top-level wire fields available on {@code /json} responses,
 * mirroring the server's {@code FlatField} registry. Used to build
 * {@code ?fields=}-filtered requests.
 *
 * <p>Two encodings are available:
 *
 * <ul>
 *   <li>{@link #wireName()} – the JSON key, joined with commas:
 *       {@code ?fields=country,city,isp}.</li>
 *   <li>{@link #bit()} – the numeric position; OR the bits of the desired
 *       fields and pass the result as {@code ?fields=N}.</li>
 * </ul>
 *
 * <p>Bit positions are pinned by the server and never renumbered; new fields
 * claim the next free bit. The 32-bit signed-int ceiling means bits 0–30 are
 * usable.
 */
public enum Field {
    STATUS(         1 << 0,  "status"),
    MESSAGE(        1 << 1,  "message"),
    QUERY(          1 << 2,  "query"),
    CONTINENT(      1 << 3,  "continent"),
    CONTINENT_CODE( 1 << 4,  "continentCode"),
    COUNTRY(        1 << 5,  "country"),
    COUNTRY_CODE(   1 << 6,  "countryCode"),
    REGION(         1 << 7,  "region"),
    REGION_NAME(    1 << 8,  "regionName"),
    CITY(           1 << 9,  "city"),
    DISTRICT(       1 << 10, "district"),
    ZIP(            1 << 11, "zip"),
    LAT(            1 << 12, "lat"),
    LON(            1 << 13, "lon"),
    TIMEZONE(       1 << 14, "timezone"),
    OFFSET(         1 << 15, "offset"),
    CURRENCY(       1 << 16, "currency"),
    ISP(            1 << 17, "isp"),
    ORG(            1 << 18, "org"),
    AS(             1 << 19, "as"),
    ASNAME(         1 << 20, "asname"),
    REVERSE(        1 << 21, "reverse"),
    MOBILE(         1 << 22, "mobile"),
    PROXY(          1 << 23, "proxy"),
    HOSTING(        1 << 24, "hosting");

    private final int bit;
    private final @Nonnull String wireName;

    Field(final int bit, final @Nonnull String wireName) {
        this.bit = bit;
        this.wireName = wireName;
    }

    public int bit() {
        return bit;
    }

    @Nonnull
    public String wireName() {
        return wireName;
    }

    /**
     * Joins the wire names of a collection of fields with commas, suitable for
     * passing as {@code ?fields=country,city,isp}.
     */
    @Nonnull
    public static String toQuery(final @Nonnull Collection<Field> fields) {
        return fields.stream().map(Field::wireName).collect(Collectors.joining(","));
    }

    /**
     * ORs the bits of a collection of fields into a single integer, suitable
     * for passing as {@code ?fields=N}.
     */
    public static int toBitmask(final @Nonnull Collection<Field> fields) {
        int mask = 0;
        for (final var field : fields) {
            mask |= field.bit;
        }
        return mask;
    }
}
