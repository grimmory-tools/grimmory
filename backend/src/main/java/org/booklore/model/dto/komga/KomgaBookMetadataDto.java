package org.booklore.model.dto.komga;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KomgaBookMetadataDto {
    private String title;
    private Boolean titleLock;
    
    private String summary;
    private Boolean summaryLock;
    
    private String number;
    private Boolean numberLock;
    
    private Float numberSort;
    private Boolean numberSortLock;
    
    private String releaseDate;
    private Boolean releaseDateLock;
    
    @Builder.Default
    private List<KomgaAuthorDto> authors = new ArrayList<>();
    private Boolean authorsLock;
    
    @Builder.Default
    private List<String> tags = new ArrayList<>();
    private Boolean tagsLock;
    
    private String isbn;
    private Boolean isbnLock;
    
    @Builder.Default
    private List<KomgaWebLinkDto> links = new ArrayList<>();
    private Boolean linksLock;
    
    private Instant created;
    private Instant lastModified;
}
