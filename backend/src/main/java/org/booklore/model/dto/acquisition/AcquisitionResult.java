package org.booklore.model.dto.acquisition;

public record AcquisitionResult(
        Long wantedBookId,
        boolean found,
        String nzbTitle,
        int confidence,
        String sabnzbdJobId,
        String message
) {
    public static AcquisitionResult dispatched(Long id, String nzbTitle, int confidence, String jobId) {
        return new AcquisitionResult(id, true, nzbTitle, confidence, jobId,
                "Dispatched to SABnzbd: " + nzbTitle);
    }

    public static AcquisitionResult notFound(Long id) {
        return new AcquisitionResult(id, false, null, 0, null,
                "No confident match found across all indexers");
    }
}
