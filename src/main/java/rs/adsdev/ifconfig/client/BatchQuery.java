package rs.adsdev.ifconfig.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * One entry in a {@code POST /batch} request body. The {@code fields} parameter
 * is an optional comma-separated whitelist applied to this item only; pass
 * {@code null} to receive every field {@link IpInfo} carries.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BatchQuery(
        @Nonnull
        String query,
        @Nullable
        String fields
) {
    public BatchQuery(@Nonnull String query) {
        this(query, null);
    }
}
