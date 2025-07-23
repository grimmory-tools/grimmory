package com.adityachandel.booklore.service.fileprocessor;

import com.adityachandel.booklore.model.enums.BookFileType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class BookFileProcessorRegistry {
    
    private final Map<BookFileType, BookFileProcessor> processorMap;
    
    public BookFileProcessorRegistry(List<BookFileProcessor> processors) {
        this.processorMap = new EnumMap<>(BookFileType.class);
        initializeProcessorMap(processors);
    }
    
    private void initializeProcessorMap(List<BookFileProcessor> processors) {
        for (BookFileProcessor processor : processors) {
            List<BookFileType> supportedTypes = processor.getSupportedTypes();
            for (BookFileType type : supportedTypes) {
                processorMap.put(type, processor);
                log.debug("Registered {} for type: {}", processor.getClass().getSimpleName(), type);
            }
        }
        log.info("Initialized BookFileProcessorRegistry with {} processors for {} types", 
                processors.size(), processorMap.size());
    }
    
    public Optional<BookFileProcessor> getProcessor(BookFileType type) {
        return Optional.ofNullable(processorMap.get(type));
    }
    
    public BookFileProcessor getProcessorOrThrow(BookFileType type) {
        return getProcessor(type)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No processor found for file type: " + type));
    }
}