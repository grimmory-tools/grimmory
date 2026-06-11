package org.booklore.grimmlink.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class GrimmlinkAuthServiceTransactionTest {

    @Test
    void authorizeKeepsHibernateSessionOpenForKoreaderUserRelation() throws NoSuchMethodException {
        Transactional transactional = GrimmlinkAuthService.class
                .getDeclaredMethod("authorize")
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }
}
