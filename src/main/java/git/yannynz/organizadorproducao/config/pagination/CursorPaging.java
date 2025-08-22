package git.yannynz.organizadorproducao.config.pagination;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

public final class CursorPaging {
    public record Key(Instant dataEntrega, Long id) {}
    public record PageEnvelope<T>(List<T> items, String nextCursor, boolean hasMore) {}

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        public static String encode(Key key) {
        if (key == null) return null;
        try {
            String json = MAPPER.writeValueAsString(key);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalArgumentException("Erro ao codificar cursor", e);
        }
    }

    public static Key decode(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            String json = new String(decoded, StandardCharsets.UTF_8);
            return MAPPER.readValue(json, Key.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cursor inválido", e);
        }
    }

    private CursorPaging() {}
}

