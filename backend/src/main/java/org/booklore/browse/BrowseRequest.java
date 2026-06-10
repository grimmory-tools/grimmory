package org.booklore.browse;

import java.util.List;
import java.util.Map;

/**
 * A normalized, resource-agnostic browse request: the query, facet selections and their logic,
 * the resolved sort terms, the pagination window, and (when the request came via a cursor) the
 * decoded cursor state. The per-API resolver builds this and resolves cursor-vs-parameter
 * precedence; this type is the immutable carrier the browse services share.
 *
 * @param query       free-text query, or null
 * @param facets      facet selections keyed by facet name; never null (may be empty)
 * @param facetLogic  how facets combine
 * @param sortTerms   resolved sort terms ending in the primary-key tiebreaker
 * @param offset      zero-based row offset
 * @param limit       page size
 * @param cursorState the decoded cursor this request came from, or null for a fresh request
 */
public record BrowseRequest(
        String query,
        Map<String, List<String>> facets,
        FacetLogic facetLogic,
        List<SortTerm> sortTerms,
        long offset,
        int limit,
        CursorState cursorState
) {

    /**
     * The fingerprint of this request's facet/query parameters.
     */
    public String paramsHash() {
        return ParamsHash.compute(query, facets, facetLogic);
    }
}
