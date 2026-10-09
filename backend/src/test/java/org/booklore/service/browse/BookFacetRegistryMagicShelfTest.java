package org.booklore.service.browse;

import org.booklore.exception.APIException;
import org.booklore.model.entity.BookEntity;
import org.booklore.service.opds.MagicShelfBookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookFacetRegistryMagicShelfTest {

    @Mock
    private MagicShelfBookService magicShelfBookService;

    @InjectMocks
    private BookFacetRegistry registry;

    @Test
    @SuppressWarnings("unchecked")
    void magicShelfValueShortCircuitsToMagicShelfSpecification() {
        Specification<BookEntity> magicSpec = mock(Specification.class);
        when(magicShelfBookService.toSpecification(7L, 42L)).thenReturn(magicSpec);

        registry.matching("shelf", List.of("magic:42"), 7L);

        verify(magicShelfBookService).toSpecification(7L, 42L);
    }

    @Test
    void regularShelfValueDoesNotTouchMagicShelfService() {
        registry.matching("shelf", List.of("3"), 7L);
        verifyNoInteractions(magicShelfBookService);
    }

    @Test
    void unknownFacetThrows() {
        assertThatThrownBy(() -> registry.matching("bogus", List.of("x"), 1L))
                .isInstanceOf(APIException.class);
    }

    @Test
    void malformedMagicShelfIdThrows() {
        assertThatThrownBy(() -> registry.matching("shelf", List.of("magic:not-a-number"), 1L))
                .isInstanceOf(APIException.class);
        assertThatThrownBy(() -> registry.matching("shelf", List.of("magic:"), 1L))
                .isInstanceOf(APIException.class);
    }

    @Test
    void emptyShelfValuesDoesNotThrow() {
        assertThatCode(() -> registry.matching("shelf", List.of(), 1L))
                .doesNotThrowAnyException();
    }

    @Test
    void blankAndNullShelfTokensAreIgnored() {
        assertThatCode(() -> registry.matching("shelf", Arrays.asList(null, "", "   "), 1L))
                .doesNotThrowAnyException();
        verifyNoInteractions(magicShelfBookService);
    }
}
