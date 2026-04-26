package org.grimmory.service.event;

import org.grimmory.model.dto.Book;

/**
 * Event published after a book has been successfully created.
 * Listeners can react to this event after the transaction commits.
 *
 * @param book the newly created book
 */
public record BookAddedEvent(Book book) {
}
