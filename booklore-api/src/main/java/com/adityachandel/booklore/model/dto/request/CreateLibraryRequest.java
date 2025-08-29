package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.dto.LibraryPath;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.LibraryScanMode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateLibraryRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String icon;
    @NotEmpty
    private List<LibraryPath> paths;
    private boolean watch;
    private LibraryScanMode scanMode;
    private BookFileType defaultBookFormat;
}
