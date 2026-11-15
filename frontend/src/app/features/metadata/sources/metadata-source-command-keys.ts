export const metadataSourceCommandKeys = {
  all: () => ['metadata-sources', 'command'] as const,
  refreshMetadata: () => [...metadataSourceCommandKeys.all(), 'refresh'] as const,
  importSidecar: () => [...metadataSourceCommandKeys.all(), 'sidecar', 'import'] as const,
  exportSidecar: () => [...metadataSourceCommandKeys.all(), 'sidecar', 'export'] as const,
  importLibrarySidecars: () => [...metadataSourceCommandKeys.all(), 'sidecar', 'import-library'] as const,
  exportLibrarySidecars: () => [...metadataSourceCommandKeys.all(), 'sidecar', 'export-library'] as const,
  setProposalStatus: () => [...metadataSourceCommandKeys.all(), 'review-tasks', 'proposal-status'] as const,
  deleteReviewTask: () => [...metadataSourceCommandKeys.all(), 'review-tasks', 'delete'] as const,
};
