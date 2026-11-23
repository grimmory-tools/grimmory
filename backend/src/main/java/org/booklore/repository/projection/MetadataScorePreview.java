package org.booklore.repository.projection;

public interface MetadataScorePreview {
    long getTotal();
    long getBelowThreshold();
    long getAtOrAboveThreshold();
    long getUnknown();
}
