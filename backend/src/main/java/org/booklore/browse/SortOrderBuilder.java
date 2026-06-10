package org.booklore.browse;

import jakarta.persistence.criteria.Order;

import java.util.List;

/**
 * Builds the JPA Orders for one registered sort key. Returns a list so a single key can
 * expand to multiple orders.
 *
 * @param <E> the entity root type
 */
@FunctionalInterface
public interface SortOrderBuilder<E> {

    List<Order> toOrders(SortContext<E> context);
}
