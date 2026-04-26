package org.grimmory.model;

import lombok.Data;

import java.util.List;

@Data
public class UploadedFileMetadata {
    private String title;
    private List<String> authors;
}
