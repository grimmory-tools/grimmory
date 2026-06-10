package org.booklore.browse;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CursorCodecTest {

    private final CursorCodec codec = new CursorCodec();

    @Test
    void roundTripsBasicState() {
        CursorState state = new CursorState(CursorCodec.CURRENT_VERSION, 80, 40, "seriesName,-seriesNumber", "abc123def456", null, null);
        CursorState decoded = codec.decode(codec.encode(state));
        assertEquals(state, decoded);
    }

    @Test
    void roundTripsRandomState() {
        CursorState state = new CursorState(
                CursorCodec.CURRENT_VERSION, 0, 20, "random", "0011aabbccdd",
                List.of(3, 1, 5), List.of(true, false, true));
        CursorState decoded = codec.decode(codec.encode(state));
        assertEquals(state, decoded);
    }

    @Test
    void encodeAlwaysStampsCurrentVersion() {
        CursorState state = new CursorState(999, 0, 20, "title", "hash00000000", null, null);
        CursorState decoded = codec.decode(codec.encode(state));
        assertEquals(CursorCodec.CURRENT_VERSION, decoded.version());
    }

    @Test
    void producesUrlSafeUnpaddedToken() {
        CursorState state = new CursorState(CursorCodec.CURRENT_VERSION, 12345, 40, "title", "hash00000000", null, null);
        String cursor = codec.encode(state);
        assertEquals(cursor.indexOf('+'), -1);
        assertEquals(cursor.indexOf('/'), -1);
        assertEquals(cursor.indexOf('='), -1);
    }

    @Test
    void blankCursorIsRejected() {
        assertThrows(InvalidCursorException.class, () -> codec.decode(null));
        assertThrows(InvalidCursorException.class, () -> codec.decode("  "));
    }

    @Test
    void garbageCursorIsRejected() {
        assertThrows(InvalidCursorException.class, () -> codec.decode("not-a-cursor!!"));
        assertThrows(InvalidCursorException.class, () -> codec.decode("YWJjZGVm"));
    }

    @Test
    void unsupportedVersionIsRejected() {
        // encode() always stamps CURRENT_VERSION, so hand-craft a token with a future version.
        CursorState future = new CursorState(2, 0, 20, "title", "hash00000000", null, null);
        byte[] json = JsonMapper.builder().build().writeValueAsBytes(future);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        assertThrows(InvalidCursorException.class, () -> codec.decode(token));
    }

    @Test
    void verifyParamsMatchPassesOnEqualHash() {
        CursorState state = new CursorState(CursorCodec.CURRENT_VERSION, 0, 20, "title", "samehash0000", null, null);
        assertDoesNotThrow(() -> codec.verifyParamsMatch(state, "samehash0000"));
    }

    @Test
    void verifyParamsMatchThrowsOnDifferentHash() {
        CursorState state = new CursorState(CursorCodec.CURRENT_VERSION, 0, 20, "title", "samehash0000", null, null);
        assertThrows(CursorMismatchException.class, () -> codec.verifyParamsMatch(state, "otherhash000"));
    }
}
