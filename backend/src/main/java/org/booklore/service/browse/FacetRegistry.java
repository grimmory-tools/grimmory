package org.booklore.service.browse;

import org.booklore.exception.ApiError;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FacetRegistry<E> {

    private final Map<String, FacetDef<E>> facets = new LinkedHashMap<>();

    public FacetRegistry(List<FacetDef<E>> facets) {
        for (FacetDef<E> facet : facets) {
            this.facets.put(facet.key(), facet);
        }
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
