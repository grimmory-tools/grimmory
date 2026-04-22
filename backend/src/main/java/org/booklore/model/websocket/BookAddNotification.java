package org.booklore.model.websocket;

import org.booklore.model.dto.Book;
import lombok.Data;

@Data
public class BookAddNotification {
    private Book addedBook;
}
