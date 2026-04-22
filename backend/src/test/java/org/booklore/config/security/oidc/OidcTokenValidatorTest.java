package org.booklore.config.security.oidc;

import com.nimbusds.jwt.JWTClaimsSet;
import org.booklore.exception.APIException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class OidcTokenValidatorTest {

    @Mock
    private OidcDiscoveryService discoveryService;

    @InjectMocks
    private OidcTokenValidator validator;

    // ── Reflection helpers ──

    private void invokeValidateIssuer(JWTClaimsSet claims, String expectedIssuer) throws Exception {
        Method method = OidcTokenValidator.class.getDeclaredMethod("validateIssuer", JWTClaimsSet.class, String.class);
        method.setAccessible(true);
        try {
            method.invoke(validator, claims, expectedIssuer);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    private void invokeValidateAudience(JWTClaimsSet claims, String clientId) throws Exception {
        Method method = OidcTokenValidator.class.getDeclaredMethod("validateAudience", JWTClaimsSet.class, String.class);
        method.setAccessible(true);
        try {
            method.invoke(validator, claims, clientId);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    private void invokeValidateAuthorizedParty(JWTClaimsSet claims, String clientId) throws Exception {
        Method method = OidcTokenValidator.class.getDeclaredMethod("validateAuthorizedParty", JWTClaimsSet.class, String.class);
        method.setAccessible(true);
        try {
            method.invoke(validator, claims, clientId);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    private void invokeValidateExpiration(JWTClaimsSet claims) throws Exception {
        Method method = OidcTokenValidator.class.getDeclaredMethod("validateExpiration", JWTClaimsSet.class);
        method.setAccessible(true);
        try {
            method.invoke(validator, claims);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    private void invokeValidateIssuedAt(JWTClaimsSet claims) throws Exception {
        Method method = OidcTokenValidator.class.getDeclaredMethod("validateIssuedAt", JWTClaimsSet.class);
        method.setAccessible(true);
        try {
            method.invoke(validator, claims);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    private void invokeValidateNonce(JWTClaimsSet claims, String expectedNonce) throws Exception {
        Method method = OidcTokenValidator.class.getDeclaredMethod("validateNonce", JWTClaimsSet.class, String.class);
        method.setAccessible(true);
        try {
            method.invoke(validator, claims, expectedNonce);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    private String invokeMapAlgToHashAlgorithm(String alg) throws Exception {
        Method method = OidcTokenValidator.class.getDeclaredMethod("mapAlgToHashAlgorithm", String.class);
        method.setAccessible(true);
        try {
            return (String) method.invoke(validator, alg);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    // ── validateIssuer ──

    @Test
    void validateIssuer_matchingIssuer_passes() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://idp.example.com")
                .build();

        assertThatCode(() -> invokeValidateIssuer(claims, "https://idp.example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateIssuer_trailingSlashNormalization_passes() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://idp.example.com/")
                .build();

        assertThatCode(() -> invokeValidateIssuer(claims, "https://idp.example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateIssuer_mismatch_throws() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://other-idp.example.com")
                .build();

        assertThatThrownBy(() -> invokeValidateIssuer(claims, "https://idp.example.com"))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("issuer mismatch");
    }

    @Test
    void validateIssuer_nullIssuerInClaims_throws() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().build();

        assertThatThrownBy(() -> invokeValidateIssuer(claims, "https://idp.example.com"))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("issuer mismatch");
    }

    // ── validateAudience ──

    @Test
    void validateAudience_containsClientId_passes() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .audience("my-client")
                .build();

        assertThatCode(() -> invokeValidateAudience(claims, "my-client"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateAudience_doesNotContainClientId_throws() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .audience("other-client")
                .build();

        assertThatThrownBy(() -> invokeValidateAudience(claims, "my-client"))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("audience");
    }

    @Test
    void validateAudience_nullAudience_throws() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().build();

        assertThatThrownBy(() -> invokeValidateAudience(claims, "my-client"))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("audience");
    }

    // ── validateAuthorizedParty ──

    @Test
    void validateAuthorizedParty_singleAudience_skipsCheck() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .audience("my-client")
                .build();

        assertThatCode(() -> invokeValidateAuthorizedParty(claims, "my-client"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateAuthorizedParty_multipleAudiences_matchingAzp_passes() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .audience(List.of("my-client", "other-client"))
                .claim("azp", "my-client")
                .build();

        assertThatCode(() -> invokeValidateAuthorizedParty(claims, "my-client"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateAuthorizedParty_multipleAudiences_mismatchedAzp_throws() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .audience(List.of("my-client", "other-client"))
                .claim("azp", "wrong-client")
                .build();

        assertThatThrownBy(() -> invokeValidateAuthorizedParty(claims, "my-client"))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("authorized party mismatch");
    }

    @Test
    void validateAuthorizedParty_multipleAudiences_noAzp_passes() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .audience(List.of("my-client", "other-client"))
                .build();

        assertThatCode(() -> invokeValidateAuthorizedParty(claims, "my-client"))
                .doesNotThrowAnyException();
    }

    // ── validateExpiration ──

    @Test
    void validateExpiration_futureExpiration_passes() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .build();

        assertThatCode(() -> invokeValidateExpiration(claims))
                .doesNotThrowAnyException();
    }

    @Test
    void validateExpiration_expiredWithinClockSkew_passes() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .expirationTime(Date.from(Instant.now().minusSeconds(15)))
                .build();

        assertThatCode(() -> invokeValidateExpiration(claims))
                .doesNotThrowAnyException();
    }

    @Test
    void validateExpiration_expiredBeyondClockSkew_throws() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .expirationTime(Date.from(Instant.now().minusSeconds(60)))
                .build();

        assertThatThrownBy(() -> invokeValidateExpiration(claims))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void validateExpiration_nullExpiration_throws() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().build();

        assertThatThrownBy(() -> invokeValidateExpiration(claims))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("missing expiration");
    }

    // ── validateIssuedAt ──

    @Test
    void validateIssuedAt_recentIat_passes() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issueTime(Date.from(Instant.now().minusSeconds(60)))
                .build();

        assertThatCode(() -> invokeValidateIssuedAt(claims))
                .doesNotThrowAnyException();
    }

    @Test
    void validateIssuedAt_oldIat_throws() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issueTime(Date.from(Instant.now().minusSeconds(600)))
                .build();

        assertThatThrownBy(() -> invokeValidateIssuedAt(claims))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("issued too long ago");
    }

    @Test
    void validateIssuedAt_nullIat_throws() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().build();

        assertThatThrownBy(() -> invokeValidateIssuedAt(claims))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("missing iat");
    }

    // ── validateNonce ──

    @Test
    void validateNonce_matchingNonce_passes() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("nonce", "abc123")
                .build();

        assertThatCode(() -> invokeValidateNonce(claims, "abc123"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateNonce_mismatchedNonce_throws() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("nonce", "abc123")
                .build();

        assertThatThrownBy(() -> invokeValidateNonce(claims, "wrong-nonce"))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("nonce mismatch");
    }

    @Test
    void validateNonce_nullNonceInToken_throws() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().build();

        assertThatThrownBy(() -> invokeValidateNonce(claims, "expected-nonce"))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("nonce mismatch");
    }

    // ── mapAlgToHashAlgorithm ──

    @Test
    void mapAlgToHashAlgorithm_rs256_returnsSha256() throws Exception {
        assertThat(invokeMapAlgToHashAlgorithm("RS256")).isEqualTo("SHA-256");
    }

    @Test
    void mapAlgToHashAlgorithm_es384_returnsSha384() throws Exception {
        assertThat(invokeMapAlgToHashAlgorithm("ES384")).isEqualTo("SHA-384");
    }

    @Test
    void mapAlgToHashAlgorithm_ps512_returnsSha512() throws Exception {
        assertThat(invokeMapAlgToHashAlgorithm("PS512")).isEqualTo("SHA-512");
    }

    @Test
    void mapAlgToHashAlgorithm_unknownAlg_defaultsToSha256() throws Exception {
        assertThat(invokeMapAlgToHashAlgorithm("UNKNOWN")).isEqualTo("SHA-256");
    }

    // ── invalidateProcessor ──

    @Test
    void invalidateProcessor_removesFromCache() {
        assertThatCode(() -> validator.invalidateProcessor("https://idp.example.com"))
                .doesNotThrowAnyException();
    }
}
