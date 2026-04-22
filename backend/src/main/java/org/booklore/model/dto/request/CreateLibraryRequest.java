package org.booklore.model.dto.request;

import org.booklore.model.dto.LibraryPath;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.IconType;
import org.booklore.model.enums.LibraryOrganizationMode;
import org.booklore.model.enums.MetadataSource;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateLibraryRequest {
    @NotBlank(message = "Library name must not be empty.")
    private String name;

    private String icon;
    private IconType iconType;

    @NotEmpty(message = "Library paths must not be empty.")
    private List<LibraryPath> paths;

    private boolean watch;
    private List<BookFileType> formatPriority;
    private List<BookFileType> allowedFormats;
    private MetadataSource metadataSource;
    private LibraryOrganizationMode organizationMode;
}
