package org.booklore.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.booklore.model.enums.BookFileType;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppLibrarySummary {
    private Long id;
    private String name;
    private String icon;
    private long bookCount;
    private List<BookFileType> allowedFormats;
    private List<PathSummary> paths;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PathSummary {
        private Long id;
        private String path;
    }
}
