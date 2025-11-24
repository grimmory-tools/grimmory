package com.adityachandel.booklore.util.kobo;


import com.adityachandel.booklore.model.dto.kobo.KoboHeaders;
import com.adityachandel.booklore.model.dto.BookloreSyncToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Slf4j
@RequiredArgsConstructor
@Component
public class BookloreSyncTokenGenerator {

    private static final String BOOKLORE_TOKEN_PREFIX = "BOOKLORE.";

    private final ObjectMapper objectMapper;
    private final Base64.Encoder base64Encoder = Base64.getEncoder().withoutPadding();
    private final Base64.Decoder base64Decoder = Base64.getDecoder();

    public BookloreSyncToken fromBase64(String base64Token) {
        try {
            if (base64Token.startsWith(BOOKLORE_TOKEN_PREFIX)) {
                byte[] decoded = base64Decoder.decode(base64Token.substring(BOOKLORE_TOKEN_PREFIX.length()));
                return objectMapper.readValue(decoded, BookloreSyncToken.class);
            }
            if (base64Token.contains(".")) {
                return BookloreSyncToken.builder()
                        .rawKoboSyncToken(base64Token)
                        .build();
            }
        } catch (Exception ignored) {

        }
        return new BookloreSyncToken();
    }

    public String toBase64(BookloreSyncToken token) {
        try {
            String json = objectMapper.writeValueAsString(token);
            return BOOKLORE_TOKEN_PREFIX + base64Encoder.encodeToString(json.getBytes());
        } catch (Exception e) {
            log.error("Failed to serialize Booklore sync token", e);
            return BOOKLORE_TOKEN_PREFIX;
        }
    }

    public BookloreSyncToken fromRequestHeaders(HttpServletRequest request) {
        if (request == null) return null;
        String tokenB64 = request.getHeader(KoboHeaders.X_KOBO_SYNCTOKEN);
        return tokenB64 != null ? fromBase64(tokenB64) : null;
    }
}
