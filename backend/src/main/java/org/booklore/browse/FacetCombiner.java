package org.booklore.browse;

import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Combines per-facet specifications into one. Cross-group combination is always conjunctive
 * (each facet narrows the result); the FacetLogic on a request governs combination within a
 * group, applied earlier by the FacetRegistry. Null specifications are ignored so callers can
 * omit a group, as facet-count self-omission does.
 */
public final class FacetCombiner {

    private FacetCombiner() {
    }

    /**
     * Conjuncts all non-null specifications. Empty or all-null input yields an unrestricted
     * (always-true) specification.
     */
    public static <E> Specification<E> and(List<Specification<E>> specs) {
        List<Specification<E>> present = new ArrayList<>();
        if (specs != null) {
            for (Specification<E> spec : specs) {
                if (spec != null) {
                    present.add(spec);
                }
            }
        }
        if (present.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return Specification.allOf(present);
    }

    @SafeVarargs
    public static <E> Specification<E> and(Specification<E>... specs) {
        return and(specs == null ? List.of() : Arrays.asList(specs));
    }
}
