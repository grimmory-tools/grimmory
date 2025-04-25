package com.adityachandel.booklore.model;

import lombok.Data;

import java.util.Set;

@Data
public class UploadedFileMetadata {
    private String title;
    private Set<String> authors;
}
