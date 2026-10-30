package org.booklore.service.browse;

import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public record FacetDef<E>(
        String key,
        String title,
        FacetFilter<E> filter,
        List<FacetValueSource<E>> groupBy,
        boolean searchable,
        FacetValueSource<E> range,
        List<String> listed,
        boolean allValues,
        Function<BrowseScope, Map<String, Specification<E>>> extraValues
) {

    public static <E> FacetDef<E> names(String key, String title, FacetFilter<E> filter, FacetValueSource<E> value) {
        return new FacetDef<>(key, title, filter, List.of(value), true, null, List.of(), false, null);
    }

    @SafeVarargs
    public static <E> FacetDef<E> values(String key, String title, FacetFilter<E> filter, FacetValueSource<E>... sources) {
        return new FacetDef<>(key, title, filter, List.of(sources), false, null, List.of(), false, null);
    }

    public static <E> FacetDef<E> number(String key, String title, FacetFilter<E> filter, FacetValueSource<E> value) {
        return new FacetDef<>(key, title, filter, List.of(), false, value, List.of(), false, null);
    }

    public static <E> FacetDef<E> choices(String key, String title, FacetFilter<E> filter, String... values) {
        return new FacetDef<>(key, title, filter, List.of(), false, null, List.of(values), false, null);
    }

    public FacetDef<E> banded(List<String> bands) {
        return new FacetDef<>(key, title, filter, groupBy, searchable, range, bands, allValues, extraValues);
    }

    public FacetDef<E> uncapped() {
        return new FacetDef<>(key, title, filter, groupBy, searchable, range, listed, true, extraValues);
    }

    public FacetDef<E> withExtraValues(Function<BrowseScope, Map<String, Specification<E>>> values) {
        return new FacetDef<>(key, title, filter, groupBy, searchable, range, listed, allValues, values);
    }
}
