package org.grimmory.repository.projection;

public interface BookEmbeddingProjection {
    Long getBookId();
    String getEmbeddingVector();
    String getSeriesName();
}
