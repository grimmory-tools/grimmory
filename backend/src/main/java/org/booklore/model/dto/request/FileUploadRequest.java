package org.booklore.model.dto.request;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class FileUploadRequest {
    private String libraryId;
    private String filePath;
    private MultipartFile file;
}
