package org.booklore.browse;

import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Set;

/**
 * Maps facet names to the specifications that apply them for an entity. One registry exists per
 * browsable resource; implementations delegate to existing specification factories where they
 * exist (the book registry reuses AppBookSpecification). Within-group combination of selected
 * values is handled here; cross-group combination is left to callers via FacetCombiner, so
 * facet-count endpoints can omit a single group when computing its own counts.
 *
 * @param <E> the entity root type
 */
public interface FacetRegistry<E> {

    boolean has(String facetName);

    Set<String> facetNames();

    /**
     * Builds the specification for one facet group's selected values.
     *
     * @param facetName the facet key; must satisfy has
     * @param values    the selected values for this facet
     * @param logic     how the selected values combine within this group
     */
    Specification<E> toSpecification(String facetName, List<String> values, FacetLogic logic);
}
