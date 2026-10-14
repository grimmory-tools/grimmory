package org.booklore.service.browse;

import java.util.List;

public record FacetDef<E>(
        String key,
        String title,
        FacetFilter<E> filter,
        List<FacetValueSource<E>> groupBy,
        boolean searchable,
        FacetValueSource<E> range,
        List<String> listed
) {

    public static <E> FacetDef<E> names(String key, String title, FacetFilter<E> filter, FacetValueSource<E> value) {
        return new FacetDef<>(key, title, filter, List.of(value), true, null, List.of());
    }

    @SafeVarargs
    public static <E> FacetDef<E> values(String key, String title, FacetFilter<E> filter, FacetValueSource<E>... sources) {
        return new FacetDef<>(key, title, filter, List.of(sources), false, null, List.of());
    }

    public static <E> FacetDef<E> number(String key, String title, FacetFilter<E> filter, FacetValueSource<E> value) {
        return new FacetDef<>(key, title, filter, List.of(), false, value, List.of());
    }

    public static <E> FacetDef<E> choices(String key, String title, FacetFilter<E> filter, String... values) {
        return new FacetDef<>(key, title, filter, List.of(), false, null, List.of(values));
    }

    public FacetDef<E> banded(List<String> bands) {
        return new FacetDef<>(key, title, filter, groupBy, searchable, range, bands);
    }
}
