package org.booklore.model;

import lombok.Data;

import java.util.List;

@Data
public class UploadedFileMetadata {
    private String title;
    private List<String> authors;
}
