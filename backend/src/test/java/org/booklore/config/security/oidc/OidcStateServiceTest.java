package org.booklore.config.security.oidc;

import org.booklore.exception.APIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OidcStateServiceTest {

    private OidcStateService oidcStateService;

    @BeforeEach
    void setUp() {
        oidcStateService = new OidcStateService();
    }

    @Test
    void generateState_returnsNonNullNonBlankString() {
        String state = oidcStateService.generateState();

        assertThat(state).isNotNull().isNotBlank();
    }

    @Test
    void generateState_returnsUniqueValuesOnMultipleCalls() {
        Set<String> states = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            states.add(oidcStateService.generateState());
        }

        assertThat(states).hasSize(100);
    }

    @Test
    void generateState_returnsBase64UrlEncodedString() {
        String state = oidcStateService.generateState();

        assertThat(state).matches("^[A-Za-z0-9_-]{43,44}$");
    }

    @Test
    void validateAndConsume_succeedsForValidGeneratedState() {
        String state = oidcStateService.generateState();

        oidcStateService.validateAndConsume(state);
    }

    @Test
    void validateAndConsume_throwsForNullState() {
        assertThatThrownBy(() -> oidcStateService.validateAndConsume(null))
                .isInstanceOf(APIException.class);
    }

    @Test
    void validateAndConsume_throwsForBlankState() {
        assertThatThrownBy(() -> oidcStateService.validateAndConsume("   "))
                .isInstanceOf(APIException.class);
    }

    @Test
    void validateAndConsume_throwsForUnknownState() {
        assertThatThrownBy(() -> oidcStateService.validateAndConsume("unknown-state-value"))
                .isInstanceOf(APIException.class);
    }

    @Test
    void validateAndConsume_stateIsSingleUse() {
        String state = oidcStateService.generateState();

        oidcStateService.validateAndConsume(state);

        assertThatThrownBy(() -> oidcStateService.validateAndConsume(state))
                .isInstanceOf(APIException.class);
    }

    @Test
    void validateAndConsume_multipleStatesCoexistIndependently() {
        String state1 = oidcStateService.generateState();
        String state2 = oidcStateService.generateState();
        String state3 = oidcStateService.generateState();

        oidcStateService.validateAndConsume(state2);

        oidcStateService.validateAndConsume(state1);
        oidcStateService.validateAndConsume(state3);
    }
}
