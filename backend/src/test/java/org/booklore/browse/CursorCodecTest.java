package org.booklore.browse;

import org.booklore.exception.APIException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CursorCodecTest {

    private final CursorCodec codec = new CursorCodec();

    @Test
    void roundTripsBasicState() {
        CursorState state = new CursorState(CursorCodec.CURRENT_VERSION, 80, 40, "seriesName,-seriesNumber", "abc123def456");
        CursorState decoded = codec.decode(codec.encode(state));
        assertEquals(state, decoded);
    }

    @Test
    void encodeAlwaysStampsCurrentVersion() {
        CursorState state = new CursorState(999, 0, 20, "title", "hash00000000");
        CursorState decoded = codec.decode(codec.encode(state));
        assertEquals(CursorCodec.CURRENT_VERSION, decoded.version());
    }

    @Test
    void producesUrlSafeUnpaddedToken() {
        CursorState state = new CursorState(CursorCodec.CURRENT_VERSION, 12345, 40, "title", "hash00000000");
        String cursor = codec.encode(state);
        assertEquals(cursor.indexOf('+'), -1);
        assertEquals(cursor.indexOf('/'), -1);
        assertEquals(cursor.indexOf('='), -1);
    }

    @Test
    void blankCursorIsRejected() {
        assertThrows(APIException.class, () -> codec.decode(null));
        assertThrows(APIException.class, () -> codec.decode("  "));
    }

    @Test
    void garbageCursorIsRejected() {
        assertThrows(APIException.class, () -> codec.decode("not-a-cursor!!"));
        assertThrows(APIException.class, () -> codec.decode("YWJjZGVm"));
    }

    @Test
    void unsupportedVersionIsRejected() {
        // encode() always stamps CURRENT_VERSION, so hand-craft a token with a future version.
        CursorState future = new CursorState(2, 0, 20, "title", "hash00000000");
        byte[] json = JsonMapper.builder().build().writeValueAsBytes(future);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        assertThrows(APIException.class, () -> codec.decode(token));
    }

    @Test
    void verifyParamsMatchPassesOnEqualHash() {
        CursorState state = new CursorState(CursorCodec.CURRENT_VERSION, 0, 20, "title", "samehash0000");
        assertDoesNotThrow(() -> codec.verifyParamsMatch(state, "samehash0000"));
    }

    @Test
    void verifyParamsMatchThrowsOnDifferentHash() {
        CursorState state = new CursorState(CursorCodec.CURRENT_VERSION, 0, 20, "title", "samehash0000");
        assertThrows(APIException.class, () -> codec.verifyParamsMatch(state, "otherhash000"));
    }
}
