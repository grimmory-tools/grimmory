package org.booklore.browse;

import org.booklore.exception.ApiError;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;
import java.util.Objects;

// Pagination cursor- unsigned, unpadded base64url of the JSON CursorState
// Parameter validation is enforced via the embedded params hash
@Component
public class CursorCodec {

    private final ObjectMapper mapper = JsonMapper.shared();

    public String encode(CursorState state) {
        byte[] json = mapper.writeValueAsBytes(state);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
    }

    public CursorState decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            throw ApiError.INVALID_CURSOR.createException("Cursor is missing.");
        }
        CursorState state;
        try {
            byte[] json = Base64.getUrlDecoder().decode(cursor);
            state = mapper.readValue(json, CursorState.class);
        } catch (Exception e) {
            throw ApiError.INVALID_CURSOR.createException("Cursor is malformed.");
        }
        if (state == null) {
            throw ApiError.INVALID_CURSOR.createException("Cursor is malformed.");
        }
        if (state.offset() < 0 || state.offset() > Integer.MAX_VALUE || state.limit() <= 0) {
            throw ApiError.INVALID_CURSOR.createException("Cursor offset/limit out of range.");
        }
        return state;
    }

    public void verifyParamsMatch(CursorState state, String currentParamsHash) {
        if (!Objects.equals(state.paramsHash(), currentParamsHash)) {
            throw ApiError.INVALID_CURSOR.createException(
                    "Cursor does not match the supplied facet/query parameters. Drop the cursor or re-send the original parameters.");
        }
    }
}
