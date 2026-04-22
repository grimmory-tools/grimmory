package org.booklore.config.security.oidc;

import com.nimbusds.jwt.JWTClaimsSet;
import org.booklore.config.security.oidc.OidcClaimExtractor.OidcUserClaims;
import org.booklore.model.dto.settings.OidcProviderDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class OidcClaimExtractorTest {

    private OidcClaimExtractor extractor;
    private OidcProviderDetails.ClaimMapping mapping;

    @BeforeEach
    void setUp() {
        extractor = new OidcClaimExtractor();
        mapping = new OidcProviderDetails.ClaimMapping();
    }

    @Test
    void extractClaims_allClaimsPresentInIdToken_usesPrimaryMapping() {
        mapping.setUsername("custom_user");
        mapping.setEmail("custom_email");
        mapping.setName("custom_name");
        mapping.setGroups("custom_groups");

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("sub-123")
                .claim("custom_user", "alice")
                .claim("custom_email", "alice@example.com")
                .claim("custom_name", "Alice Smith")
                .claim("custom_groups", List.of("admin", "users"))
                .build();

        OidcUserClaims result = extractor.extractClaims(claims, mapping, Map.of());

        assertThat(result.username()).isEqualTo("alice");
        assertThat(result.email()).isEqualTo("alice@example.com");
        assertThat(result.name()).isEqualTo("Alice Smith");
        assertThat(result.subject()).isEqualTo("sub-123");
        assertThat(result.groups()).containsExactly("admin", "users");
    }

    @Test
    void extractClaims_fallsBackToDefaultClaimNames_whenPrimaryMappingIsNull() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("sub-456")
                .claim("preferred_username", "bob")
                .claim("email", "bob@example.com")
                .claim("name", "Bob Jones")
                .claim("groups", List.of("readers"))
                .build();

        OidcUserClaims result = extractor.extractClaims(claims, mapping, Map.of());

        assertThat(result.username()).isEqualTo("bob");
        assertThat(result.email()).isEqualTo("bob@example.com");
        assertThat(result.name()).isEqualTo("Bob Jones");
        assertThat(result.subject()).isEqualTo("sub-456");
        assertThat(result.groups()).containsExactly("readers");
    }

    @Test
    void extractClaims_fallsBackToUserInfo_whenIdTokenClaimsMissing() {
        mapping.setUsername("custom_user");
        mapping.setEmail("custom_email");
        mapping.setName("custom_name");
        mapping.setGroups("custom_groups");

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("sub-789")
                .build();

        Map<String, Object> userInfo = Map.of(
                "custom_user", "charlie",
                "custom_email", "charlie@example.com",
                "custom_name", "Charlie Brown",
                "custom_groups", List.of("editors")
        );

        OidcUserClaims result = extractor.extractClaims(claims, mapping, userInfo);

        assertThat(result.username()).isEqualTo("charlie");
        assertThat(result.email()).isEqualTo("charlie@example.com");
        assertThat(result.name()).isEqualTo("Charlie Brown");
        assertThat(result.groups()).containsExactly("editors");
    }

    @Test
    void extractClaims_fallsBackToUserInfoFallbackClaims() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("sub-abc")
                .build();

        Map<String, Object> userInfo = Map.of(
                "preferred_username", "dana",
                "email", "dana@example.com",
                "name", "Dana White",
                "groups", List.of("viewers")
        );

        OidcUserClaims result = extractor.extractClaims(claims, mapping, userInfo);

        assertThat(result.username()).isEqualTo("dana");
        assertThat(result.email()).isEqualTo("dana@example.com");
        assertThat(result.name()).isEqualTo("Dana White");
        assertThat(result.groups()).containsExactly("viewers");
    }

    @Test
    void extractClaims_usernameFallsBackToEmail_whenUsernameIsNull() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("sub-def")
                .claim("email", "eve@example.com")
                .build();

        OidcUserClaims result = extractor.extractClaims(claims, mapping, Map.of());

        assertThat(result.username()).isEqualTo("eve@example.com");
        assertThat(result.email()).isEqualTo("eve@example.com");
    }

    @Test
    void extractClaims_usernameFallsBackToSubject_whenBothUsernameAndEmailNull() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("sub-ghi")
                .build();

        OidcUserClaims result = extractor.extractClaims(claims, mapping, Map.of());

        assertThat(result.username()).isEqualTo("sub-ghi");
        assertThat(result.email()).isNull();
    }

    @Test
    void extractClaims_usernameFallsBackToSubject_whenUsernameIsBlank() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("sub-jkl")
                .claim("preferred_username", "   ")
                .build();

        OidcUserClaims result = extractor.extractClaims(claims, mapping, Map.of());

        assertThat(result.username()).isEqualTo("sub-jkl");
    }

    @Test
    void extractClaims_groupsExtractedFromIdTokenPrimaryClaim() {
        mapping.setGroups("roles");

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("sub-mno")
                .claim("preferred_username", "frank")
                .claim("roles", List.of("admin", "superuser"))
                .build();

        OidcUserClaims result = extractor.extractClaims(claims, mapping, Map.of());

        assertThat(result.groups()).containsExactly("admin", "superuser");
    }

    @Test
    void extractClaims_groupsFallBackToUserInfo() {
        mapping.setGroups("roles");

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("sub-pqr")
                .claim("preferred_username", "grace")
                .build();

        Map<String, Object> userInfo = Map.of(
                "roles", List.of("contributor")
        );

        OidcUserClaims result = extractor.extractClaims(claims, mapping, userInfo);

        assertThat(result.groups()).containsExactly("contributor");
    }

    @Test
    void extractClaims_groupsReturnEmptyList_whenNothingFound() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("sub-stu")
                .claim("preferred_username", "hank")
                .build();

        OidcUserClaims result = extractor.extractClaims(claims, mapping, Map.of());

        assertThat(result.groups()).isEmpty();
    }

    @Test
    void extractClaims_pictureUrlExtractedFromUserInfoFallback() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("sub-vwx")
                .claim("preferred_username", "iris")
                .build();

        Map<String, Object> userInfo = Map.of(
                "picture", "https://example.com/avatar.jpg"
        );

        OidcUserClaims result = extractor.extractClaims(claims, mapping, userInfo);

        assertThat(result.pictureUrl()).isEqualTo("https://example.com/avatar.jpg");
    }

    @Test
    void extractClaims_pictureUrlExtractedFromIdToken() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("sub-pic")
                .claim("preferred_username", "jake")
                .claim("picture", "https://example.com/id-token-avatar.jpg")
                .build();

        OidcUserClaims result = extractor.extractClaims(claims, mapping, Map.of());

        assertThat(result.pictureUrl()).isEqualTo("https://example.com/id-token-avatar.jpg");
    }

    @Test
    void extractClaims_subjectAlwaysComesFromClaimsGetSubject() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("unique-sub-id")
                .claim("preferred_username", "kate")
                .build();

        Map<String, Object> userInfo = Map.of("sub", "different-sub");

        OidcUserClaims result = extractor.extractClaims(claims, mapping, userInfo);

        assertThat(result.subject()).isEqualTo("unique-sub-id");
    }

    @Test
    void extractClaims_handlesNullUserInfoGracefully() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("sub-null-ui")
                .claim("preferred_username", "leo")
                .claim("email", "leo@example.com")
                .claim("name", "Leo Messi")
                .claim("groups", List.of("team"))
                .build();

        OidcUserClaims result = extractor.extractClaims(claims, mapping, null);

        assertThat(result.username()).isEqualTo("leo");
        assertThat(result.email()).isEqualTo("leo@example.com");
        assertThat(result.name()).isEqualTo("Leo Messi");
        assertThat(result.subject()).isEqualTo("sub-null-ui");
        assertThat(result.groups()).containsExactly("team");
        assertThat(result.pictureUrl()).isNull();
    }

    @Test
    void extractClaims_emptyMappingUsesDefaults() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("sub-empty-map")
                .claim("preferred_username", "maya")
                .claim("email", "maya@example.com")
                .claim("name", "Maya Lin")
                .claim("groups", List.of("architects"))
                .claim("picture", "https://example.com/maya.jpg")
                .build();

        OidcUserClaims result = extractor.extractClaims(claims, mapping, Map.of());

        assertThat(result.username()).isEqualTo("maya");
        assertThat(result.email()).isEqualTo("maya@example.com");
        assertThat(result.name()).isEqualTo("Maya Lin");
        assertThat(result.subject()).isEqualTo("sub-empty-map");
        assertThat(result.pictureUrl()).isEqualTo("https://example.com/maya.jpg");
        assertThat(result.groups()).containsExactly("architects");
    }
}
