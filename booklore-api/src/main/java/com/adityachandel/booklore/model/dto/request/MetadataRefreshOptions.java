package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.enums.MetadataProvider;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MetadataRefreshOptions {
    @NotNull(message = "Default Provider cannot be null")
    private MetadataProvider allP1;
    private MetadataProvider allP2;
    private MetadataProvider allP3;
    private boolean refreshCovers;
    private boolean mergeCategories;
    private FieldOptions fieldOptions;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldOptions {
        private FieldProvider title;
        private FieldProvider subtitle;
        private FieldProvider description;
        private FieldProvider authors;
        private FieldProvider publisher;
        private FieldProvider publishedDate;
        private FieldProvider seriesName;
        private FieldProvider seriesNumber;
        private FieldProvider seriesTotal;
        private FieldProvider isbn13;
        private FieldProvider isbn10;
        private FieldProvider language;
        private FieldProvider categories;
        private FieldProvider cover;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldProvider {
        private MetadataProvider p3;
        private MetadataProvider p2;
        private MetadataProvider p1;
    }
}
