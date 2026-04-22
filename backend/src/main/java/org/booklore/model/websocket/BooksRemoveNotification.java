package org.booklore.model.websocket;

import lombok.Data;

import java.util.Set;

@Data
public class BooksRemoveNotification {
    private Set<Long> removedBookIds;
}
