package org.booklore.browse;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FacetCombinerTest {

    @SuppressWarnings("unchecked")
    private final Root<Object> root = mock(Root.class);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder cb = mock(CriteriaBuilder.class);

    @Test
    void emptyListYieldsConjunction() {
        Predicate conjunction = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(conjunction);

        Specification<Object> spec = FacetCombiner.and(List.of());
        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(conjunction, result);
        verify(cb).conjunction();
    }

    @Test
    void allNullSpecificationsYieldConjunction() {
        Predicate conjunction = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(conjunction);

        Specification<Object> spec = FacetCombiner.and(Arrays.asList(null, null));
        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(conjunction, result);
    }

    @Test
    void nullListYieldsConjunction() {
        when(cb.conjunction()).thenReturn(mock(Predicate.class));
        assertDoesNotThrow(() -> FacetCombiner.and((List<Specification<Object>>) null).toPredicate(root, query, cb));
    }

    @Test
    void singlePresentSpecificationIsApplied() {
        Predicate predicate = mock(Predicate.class);
        Specification<Object> present = (r, q, c) -> predicate;

        Specification<Object> spec = FacetCombiner.and(Arrays.asList(present, null));

        assertNotNull(spec);
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    @Test
    void varargsToleratesNulls() {
        when(cb.conjunction()).thenReturn(mock(Predicate.class));
        Specification<Object> present = (r, q, c) -> mock(Predicate.class);
        assertDoesNotThrow(() -> FacetCombiner.and(present, null).toPredicate(root, query, cb));
    }
}
