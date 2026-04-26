package org.grimmory.model.websocket;

import org.grimmory.model.dto.Book;
import lombok.Data;

@Data
public class BookAddNotification {
    private Book addedBook;
}
