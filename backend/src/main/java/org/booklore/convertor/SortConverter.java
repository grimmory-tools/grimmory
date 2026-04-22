package org.booklore.convertor;

import org.booklore.model.dto.Sort;
import org.booklore.model.enums.SortDirection;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class SortConverter implements AttributeConverter<Sort, String> {

    @Override
    public String convertToDatabaseColumn(Sort sort) {
        if (sort == null) {
            return null;
        }
        return sort.getField() + "," + sort.getDirection().name();
    }

    @Override
    public Sort convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        String[] parts = dbData.split(",");
        Sort sort = new Sort();
        sort.setField(parts[0]);
        sort.setDirection(SortDirection.valueOf(parts[1]));
        return sort;
    }
}
