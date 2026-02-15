package org.booklore.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.booklore.model.dto.Book;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookSyncResponse {
    private List<Book> books;
    private List<Long> deletedIds;
    private String syncTimestamp;
    private long totalBookCount;
}
