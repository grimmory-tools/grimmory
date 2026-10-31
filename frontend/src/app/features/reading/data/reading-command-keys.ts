export const readingCommandKeys = {
  all: () => ['reading', 'command'] as const,
  createAnnotation: () => [...readingCommandKeys.all(), 'annotation', 'create'] as const,
  deleteAnnotation: () => [...readingCommandKeys.all(), 'annotation', 'delete'] as const,
  createBookmark: () => [...readingCommandKeys.all(), 'bookmark', 'create'] as const,
  deleteBookmark: () => [...readingCommandKeys.all(), 'bookmark', 'delete'] as const,
  createNote: () => [...readingCommandKeys.all(), 'note', 'create'] as const,
  updateNote: () => [...readingCommandKeys.all(), 'note', 'update'] as const,
  deleteNote: () => [...readingCommandKeys.all(), 'note', 'delete'] as const,
  createBookNote: () => [...readingCommandKeys.all(), 'book-note', 'create'] as const,
  updateBookNote: () => [...readingCommandKeys.all(), 'book-note', 'update'] as const,
  deleteBookNote: () => [...readingCommandKeys.all(), 'book-note', 'delete'] as const,
  savePdfAnnotations: () => [...readingCommandKeys.all(), 'pdf-annotations', 'save'] as const,
  saveViewerSettings: () => [...readingCommandKeys.all(), 'viewer-settings', 'save'] as const,
  recordReadingSession: () => [...readingCommandKeys.all(), 'session', 'record'] as const,
};

export const readingCommandScopes = {
  pdfAnnotations: {id: 'reading.command.pdf-annotations'} as const,
  viewerSettings: {id: 'reading.command.viewer-settings'} as const,
};
