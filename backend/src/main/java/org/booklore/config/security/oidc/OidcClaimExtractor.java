package org.booklore.config.security.oidc;

import com.nimbusds.jwt.JWTClaimsSet;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.settings.OidcProviderDetails;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class OidcClaimExtractor {

    public record OidcUserClaims(
            String username,
            String email,
            String name,
            String subject,
            String pictureUrl,
            List<String> groups
    ) {}

    public OidcUserClaims extractClaims(JWTClaimsSet claims, OidcProviderDetails.ClaimMapping mapping, Map<String, Object> userInfo) {
        String username = getStringClaim(claims, userInfo, mapping.getUsername(), "preferred_username");
        String email = getStringClaim(claims, userInfo, mapping.getEmail(), "email");
        String name = getStringClaim(claims, userInfo, mapping.getName(), "name");
        String subject = claims.getSubject();
        String pictureUrl = getStringClaim(claims, userInfo, null, "picture");
        List<String> groups = getListClaim(claims, userInfo, mapping.getGroups(), "groups");

        if (username == null || username.isBlank()) {
            username = email != null ? email : subject;
        }

        return new OidcUserClaims(username, email, name, subject, pictureUrl, groups);
    }

    private List<String> getListClaim(JWTClaimsSet claims, Map<String, Object> userInfo, String primaryClaim, String fallbackClaim) {
        List<String> value = tryGetListClaim(claims, primaryClaim);
        if (value == null && fallbackClaim != null && !fallbackClaim.equals(primaryClaim)) {
            value = tryGetListClaim(claims, fallbackClaim);
        }
        if (value == null) {
            value = tryGetUserInfoListClaim(userInfo, primaryClaim);
        }
        if (value == null && fallbackClaim != null && !fallbackClaim.equals(primaryClaim)) {
            value = tryGetUserInfoListClaim(userInfo, fallbackClaim);
        }
        return value != null ? value : List.of();
    }

    private List<String> tryGetListClaim(JWTClaimsSet claims, String claimName) {
        if (claimName == null || claimName.isBlank()) return null;
        try {
            List<String> list = claims.getStringListClaim(claimName);
            return list != null && !list.isEmpty() ? list : null;
        } catch (ParseException e) {
            log.debug("Failed to parse list claim '{}': {}", claimName, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> tryGetUserInfoListClaim(Map<String, Object> userInfo, String claimName) {
        if (claimName == null || claimName.isBlank() || userInfo == null) return null;
        Object value = userInfo.get(claimName);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String s) result.add(s);
            }
            return result.isEmpty() ? null : result;
        }
        return null;
    }

    private String getStringClaim(JWTClaimsSet claims, Map<String, Object> userInfo, String primaryClaim, String fallbackClaim) {
        String value = tryGetClaim(claims, primaryClaim);
        if (value == null && fallbackClaim != null && !fallbackClaim.equals(primaryClaim)) {
            value = tryGetClaim(claims, fallbackClaim);
        }
        if (value == null) {
            value = tryGetUserInfoClaim(userInfo, primaryClaim);
        }
        if (value == null && fallbackClaim != null && !fallbackClaim.equals(primaryClaim)) {
            value = tryGetUserInfoClaim(userInfo, fallbackClaim);
        }
        return value;
    }

    private String tryGetClaim(JWTClaimsSet claims, String claimName) {
        if (claimName == null || claimName.isBlank()) return null;
        try {
            return claims.getStringClaim(claimName);
        } catch (ParseException e) {
            log.debug("Failed to parse claim '{}': {}", claimName, e.getMessage());
            return null;
        }
    }

    private String tryGetUserInfoClaim(Map<String, Object> userInfo, String claimName) {
        if (claimName == null || claimName.isBlank() || userInfo == null) return null;
        Object value = userInfo.get(claimName);
        return value instanceof String s ? s : null;
    }
}
