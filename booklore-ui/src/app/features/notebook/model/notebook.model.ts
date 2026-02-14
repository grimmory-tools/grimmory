export interface NotebookEntry {
  id: number;
  type: 'HIGHLIGHT' | 'NOTE' | 'BOOKMARK';
  bookId: number;
  bookTitle: string;
  text: string;
  note?: string;
  color?: string;
  style?: string;
  chapterTitle?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface NotebookPage {
  content: NotebookEntry[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface NotebookBookOption {
  bookId: number;
  bookTitle: string;
}
