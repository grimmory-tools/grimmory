package org.booklore.grimmlink.facade;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class GrimmlinkFacadeTransactionTest {

    @Test
    void authorizeKeepsHibernateSessionOpenForKoreaderUserRelation() throws NoSuchMethodException {
        Transactional transactional = GrimmlinkFacade.class
                .getDeclaredMethod("authorize")
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }
}
