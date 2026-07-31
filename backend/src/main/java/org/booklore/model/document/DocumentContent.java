package org.booklore.model.document;

import java.time.LocalDate;
import java.util.List;

/**
 * A Word document reduced to what this application actually reads: ordered text blocks and their
 * heading hierarchy. Produced once per document and shared by every consumer - metadata, the
 * full-text index, and the reader rendition - so a document is never parsed twice for two purposes.
 */
public record DocumentContent(
        List<DocumentBlock> blocks,
        String title,
        String author,
        LocalDate createdDate) {

    public DocumentContent {
        blocks = List.copyOf(blocks);
    }

    public DocumentContent(List<DocumentBlock> blocks) {
        this(blocks, null, null, null);
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }
}
