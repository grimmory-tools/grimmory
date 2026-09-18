package org.booklore.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.booklore.model.dto.BookMetadata;

import java.util.Optional;

@Data
@NoArgsConstructor
public class MetadataUpdateWrapper {
    private BookMetadata metadata;
    private MetadataClearFlags clearFlags = new MetadataClearFlags();

    @Builder
    private MetadataUpdateWrapper(BookMetadata metadata, MetadataClearFlags clearFlags) {
        // @Builder.Default does not work as expected with non-primitive types in all cases
        // so we've created our own helper.
        this.metadata = metadata;
        this.clearFlags = Optional.ofNullable(clearFlags).orElse(this.clearFlags);
    }
}
