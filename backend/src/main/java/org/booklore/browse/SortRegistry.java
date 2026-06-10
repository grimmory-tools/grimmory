package org.booklore.browse;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps sort key names to the builders that turn them into JPA orders for an entity. One
 * registry exists per browsable resource; its registered keys are the allow-list SortParser
 * validates against. Every registry must register the primary-key tiebreaker key ("id") so the
 * implicit final sort SortParser appends can be resolved.
 *
 * @param <E> the entity root type
 */
public class SortRegistry<E> {

    private final Map<String, SortOrderBuilder<E>> builders = new LinkedHashMap<>();

    /**
     * Registers a sort key. Returns this for chaining.
     */
    public SortRegistry<E> register(String key, SortOrderBuilder<E> builder) {
        builders.put(key, builder);
        return this;
    }

    public boolean has(String key) {
        return builders.containsKey(key);
    }

    /**
     * The registered key names, in registration order; the validation allow-list for SortParser.
     */
    public Set<String> keys() {
        return Collections.unmodifiableSet(builders.keySet());
    }

    /**
     * Resolves parsed sort terms into the JPA orders to apply, in order.
     *
     * @throws InvalidSortException if a term references an unregistered key
     */
    public List<Order> toOrders(List<SortTerm> terms, Root<E> root, CriteriaQuery<?> query, CriteriaBuilder cb, Long userId) {
        List<Order> orders = new ArrayList<>();
        for (SortTerm term : terms) {
            SortOrderBuilder<E> builder = builders.get(term.key());
            if (builder == null) {
                throw new InvalidSortException("Unknown sort key: " + term.key());
            }
            orders.addAll(builder.toOrders(new SortContext<>(root, query, cb, term.descending(), userId)));
        }
        return orders;
    }
}
