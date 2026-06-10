package org.booklore.browse;

/**
 * A single resolved sort instruction: a sort key plus its direction.
 *
 * @param key        the sort key name, as registered in a SortRegistry
 * @param descending true for descending order, false for ascending
 */
public record SortTerm(String key, boolean descending) {
}
