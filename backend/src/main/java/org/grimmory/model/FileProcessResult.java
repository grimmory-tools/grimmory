package org.grimmory.model;

import lombok.*;
import org.grimmory.model.dto.Book;
import org.grimmory.model.enums.FileProcessStatus;

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
