package org.booklore.service.browse;

import org.booklore.exception.ApiError;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class FacetRegistry<E> {

    private final Map<String, FacetDef<E>> facets = new LinkedHashMap<>();

    public FacetRegistry<E> register(FacetDef<E> facet) {
        facets.put(facet.key(), facet);
        return this;
    }

    public FacetDef<E> find(String key) {
        FacetDef<E> facet = facets.get(key);
        if (facet == null) {
            throw ApiError.INVALID_FACET.createException("Unknown facet: " + key);
        }
        return facet;
    }

    public Collection<FacetDef<E>> all() {
        return Collections.unmodifiableCollection(facets.values());
    }
}
