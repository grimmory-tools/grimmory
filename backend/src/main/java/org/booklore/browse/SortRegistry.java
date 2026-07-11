package org.booklore.browse;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import org.booklore.exception.ApiError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Maps sort keys to JPA order builders for one browsable entity
// Registered keys are the allow-list SortParser validates against
// Every registry must register the "id" tiebreaker.
public class SortRegistry<E> {

    private final Map<String, SortOrderBuilder<E>> builders = new LinkedHashMap<>();

    public SortRegistry<E> register(String key, SortOrderBuilder<E> builder) {
        builders.put(key, builder);
        return this;
    }

    public boolean has(String key) {
        return builders.containsKey(key);
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(builders.keySet());
    }

    public List<Order> toOrders(List<SortTerm> terms, Root<E> root, CriteriaQuery<?> query, CriteriaBuilder cb, Long userId) {
        List<Order> orders = new ArrayList<>();
        for (SortTerm term : terms) {
            SortOrderBuilder<E> builder = builders.get(term.key());
            if (builder == null) {
                throw ApiError.INVALID_SORT.createException("Unknown sort key: " + term.key());
            }
            orders.addAll(builder.toOrders(new SortContext<>(root, query, cb, term.descending(), userId)));
        }
        return orders;
    }
}
