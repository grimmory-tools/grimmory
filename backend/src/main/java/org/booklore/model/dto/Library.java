package org.booklore.model.dto;

import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.IconType;
import org.booklore.model.enums.LibraryOrganizationMode;
import org.booklore.model.enums.MetadataSource;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Library {
    private Long id;
    private String name;
    private Sort sort;
    private String icon;
    private IconType iconType;
    private String fileNamingPattern;
    private boolean watch;
    private List<LibraryPath> paths;
    private List<BookFileType> formatPriority;
    private List<BookFileType> allowedFormats;
    private LibraryOrganizationMode organizationMode;
    private MetadataSource metadataSource;
}

