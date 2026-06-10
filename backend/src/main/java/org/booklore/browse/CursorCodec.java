package org.booklore.browse;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;
import java.util.Objects;

/**
 * Encodes and decodes the opaque pagination cursor. The wire format is unpadded base64url of
 * the JSON CursorState. The cursor carries pagination state, not authorization, so it is not
 * signed; parameter integrity is checked via the embedded params hash.
 */
@Component
public class CursorCodec {

    public static final int CURRENT_VERSION = 1;

    private final ObjectMapper mapper = JsonMapper.builder().build();

    public String encode(CursorState state) {
        byte[] json = mapper.writeValueAsBytes(state.withVersion(CURRENT_VERSION));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
    }

    /**
     * Decodes a cursor token back into its state.
     *
     * @throws InvalidCursorException if the cursor is blank, undecodable, or carries an unsupported version
     */
    public CursorState decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            throw new InvalidCursorException("Cursor is missing.");
        }
        CursorState state;
        try {
            byte[] json = Base64.getUrlDecoder().decode(cursor);
            state = mapper.readValue(json, CursorState.class);
        } catch (Exception e) {
            throw new InvalidCursorException("Cursor is malformed.");
        }
        if (state == null) {
            throw new InvalidCursorException("Cursor is malformed.");
        }
        if (state.version() != CURRENT_VERSION) {
            throw new InvalidCursorException("Unsupported cursor version: " + state.version());
        }
        return state;
    }

    /**
     * Verifies the request's facet/query parameters match those frozen into the cursor.
     *
     * @throws CursorMismatchException if the fingerprints differ
     */
    public void verifyParamsMatch(CursorState state, String currentParamsHash) {
        if (!Objects.equals(state.paramsHash(), currentParamsHash)) {
            throw new CursorMismatchException(
                    "Cursor does not match the supplied facet/query parameters. Drop the cursor or re-send the original parameters.");
        }
    }
}
