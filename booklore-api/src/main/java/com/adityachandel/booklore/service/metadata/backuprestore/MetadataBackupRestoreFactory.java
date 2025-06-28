package com.adityachandel.booklore.service.metadata.backuprestore;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.enums.BookFileType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class MetadataBackupRestoreFactory {

    private final Map<BookFileType, MetadataBackupRestore> serviceMap;

    public MetadataBackupRestoreFactory(List<MetadataBackupRestore> services) {
        serviceMap = services.stream()
                .collect(Collectors.toMap(MetadataBackupRestore::getSupportedBookType, Function.identity()));
    }

    public MetadataBackupRestore getService(BookFileType bookType) {
        MetadataBackupRestore service = serviceMap.get(bookType);
        if (service == null) {
            throw ApiError.UNSUPPORTED_FILE_TYPE.createException("No backup service for file type: " + bookType);
        }
        return service;
    }
}
