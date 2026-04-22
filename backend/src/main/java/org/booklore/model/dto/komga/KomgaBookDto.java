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
public class KomgaBookDto {
    private String id;
    private String seriesId;
    private String seriesTitle;
    private String libraryId;
    private String name;
    private String url;
    private Integer number;
    private Instant created;
    private Instant lastModified;
    private Instant fileLastModified;
    private Long sizeBytes;
    private String size;
    private KomgaMediaDto media;
    private KomgaBookMetadataDto metadata;
    private KomgaReadProgressDto readProgress;
    private Boolean deleted;
    private String fileHash;
    private Boolean oneshot;
}