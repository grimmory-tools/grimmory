package org.booklore.util;

import lombok.experimental.UtilityClass;

import java.security.SecureRandom;

@UtilityClass
public class BookCoverUtils {

    private static final String HASH_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateCoverHash() {
        StringBuilder hash = new StringBuilder(13);
        hash.append("BL-");
        for (int i = 0; i < 13; i++) {
            hash.append(HASH_CHARS.charAt(RANDOM.nextInt(HASH_CHARS.length())));
        }
        return hash.toString();
    }
}
