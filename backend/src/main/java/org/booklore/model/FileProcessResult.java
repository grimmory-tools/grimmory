package org.booklore.model;

import lombok.*;
import org.booklore.model.dto.Book;
import org.booklore.model.enums.FileProcessStatus;

@Builder
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class FileProcessResult {
    private Book book;
    private FileProcessStatus status;
}
