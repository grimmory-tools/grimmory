package org.booklore.model.dto.komga;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KomgaSeriesDto {
    private String id;
    private String libraryId;
    private String name;
    private String url;
    private Instant created;
    private Instant lastModified;
    private Instant fileLastModified;
    private Integer booksCount;
    private Integer booksReadCount;
    private Integer booksUnreadCount;
    private Integer booksInProgressCount;
    private KomgaSeriesMetadataDto metadata;
    private KomgaBookMetadataAggregationDto booksMetadata;
    private Boolean deleted;
    private Boolean oneshot;
}
