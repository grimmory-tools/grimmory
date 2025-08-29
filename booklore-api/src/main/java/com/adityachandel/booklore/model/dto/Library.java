package com.adityachandel.booklore.model.dto;

import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.LibraryScanMode;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Library {
    private Long id;
    private String name;
    private Sort sort;
    private String icon;
    private String fileNamingPattern;
    private boolean watch;
    private List<LibraryPath> paths;
    private LibraryScanMode scanMode;
    private BookFileType defaultBookFormat;
}

