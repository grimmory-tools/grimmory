package org.booklore.browse;

import jakarta.persistence.criteria.Order;

import java.util.List;

// Builds the JPA orders for one sort key (a list so one key can expand to support multiple orders)
@FunctionalInterface
public interface SortOrderBuilder<E> {

    List<Order> toOrders(SortContext<E> context);
}
