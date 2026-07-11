package org.booklore.service.browse;

import org.booklore.browse.FacetLogic;
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

        registry.toSpecification("shelf", List.of("magic:42"), FacetLogic.OR, 7L);

        verify(magicShelfBookService).toSpecification(7L, 42L);
    }

    @Test
    void regularShelfValueDoesNotTouchMagicShelfService() {
        registry.toSpecification("shelf", List.of("3"), FacetLogic.OR, 7L);
        verifyNoInteractions(magicShelfBookService);
    }

    @Test
    void unknownFacetThrows() {
        assertThatThrownBy(() -> registry.toSpecification("bogus", List.of("x"), FacetLogic.AND, 1L))
                .isInstanceOf(APIException.class);
    }

    @Test
    void malformedMagicShelfIdThrows() {
        assertThatThrownBy(() -> registry.toSpecification("shelf", List.of("magic:not-a-number"), FacetLogic.AND, 1L))
                .isInstanceOf(APIException.class);
        assertThatThrownBy(() -> registry.toSpecification("shelf", List.of("magic:"), FacetLogic.AND, 1L))
                .isInstanceOf(APIException.class);
    }

    @Test
    void emptyShelfValuesDoesNotThrow() {
        assertThatCode(() -> registry.toSpecification("shelf", List.of(), FacetLogic.AND, 1L))
                .doesNotThrowAnyException();
    }

    @Test
    void blankAndNullShelfTokensAreIgnored() {
        assertThatCode(() -> registry.toSpecification("shelf", Arrays.asList(null, "", "   "), FacetLogic.AND, 1L))
                .doesNotThrowAnyException();
        verifyNoInteractions(magicShelfBookService);
    }
}
