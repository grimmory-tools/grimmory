import {inject, Injectable} from '@angular/core';
import {Observable, throwError} from 'rxjs';
import {HttpClient} from '@angular/common/http';
import {catchError, tap} from 'rxjs/operators';
import {AdditionalFile, AdditionalFileType, Book, DetachBookFileResponse, DuplicateDetectionRequest, DuplicateGroup} from '../model/book.model';
import {API_CONFIG} from '../../../core/config/api-config';
import {MessageService} from 'primeng/api';
import {FileDownloadService} from '../../../shared/service/file-download.service';
import {BookStateService} from './book-state.service';
import {TranslocoService} from '@jsverse/transloco';

@Injectable({
  providedIn: 'root',
})
export class BookFileService {

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/books`;

  private http = inject(HttpClient);
  private messageService = inject(MessageService);
  private fileDownloadService = inject(FileDownloadService);
  private bookStateService = inject(BookStateService);
  private readonly t = inject(TranslocoService);

  getFileContent(bookId: number, bookType?: string): Observable<Blob> {
    let url = `${this.url}/${bookId}/content`;
    if (bookType) {
      url += `?bookType=${bookType}`;
    }
    return this.http.get<Blob>(url, {responseType: 'blob' as 'json'});
  }

  downloadFile(book: Book): void {
    const downloadUrl = `${this.url}/${book.id}/download`;
    this.fileDownloadService.downloadFile(downloadUrl, book.primaryFile?.fileName ?? 'book');
  }

  downloadAllFiles(book: Book): void {
    const downloadUrl = `${this.url}/${book.id}/download-all`;
    const filename = book.metadata?.title
      ? `${book.metadata.title.replace(/[^a-zA-Z0-9\-_]/g, '_')}.zip`
      : `book-${book.id}.zip`;
    this.fileDownloadService.downloadFile(downloadUrl, filename);
  }

  deleteAdditionalFile(bookId: number, fileId: number): Observable<void> {
    const deleteUrl = `${this.url}/${bookId}/files/${fileId}`;
    return this.http.delete<void>(deleteUrl).pipe(
      tap(() => {
        const currentState = this.bookStateService.getCurrentBookState();
        const updatedBooks = (currentState.books || []).map(book => {
          if (book.id === bookId) {
            return {
              ...book,
              alternativeFormats: book.alternativeFormats?.filter(file => file.id !== fileId),
              supplementaryFiles: book.supplementaryFiles?.filter(file => file.id !== fileId)
            };
          }
          return book;
        });

        this.bookStateService.updateBookState({
          ...currentState,
          books: updatedBooks
        });

        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('book.bookService.toast.fileDeletedSummary'),
          detail: this.t.translate('book.bookService.toast.additionalFileDeletedDetail')
        });
      }),
      catchError(error => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('book.bookService.toast.fileDeleteFailedSummary'),
          detail: error?.error?.message || error?.message || this.t.translate('book.bookService.toast.fileDeleteFailedDetail')
        });
        return throwError(() => error);
      })
    );
  }

  deleteBookFile(bookId: number, fileId: number, isPrimary: boolean): Observable<void> {
    const deleteUrl = `${this.url}/${bookId}/files/${fileId}`;
    return this.http.delete<void>(deleteUrl).pipe(
      tap(() => {
        const currentState = this.bookStateService.getCurrentBookState();
        const updatedBooks = (currentState.books || []).map(book => {
          if (book.id === bookId) {
            if (isPrimary) {
              const remainingAlternatives = book.alternativeFormats?.filter(file => file.id !== fileId) || [];
              if (remainingAlternatives.length > 0) {
                const [newPrimary, ...restAlternatives] = remainingAlternatives;
                return {
                  ...book,
                  primaryFile: newPrimary,
                  alternativeFormats: restAlternatives
                };
              } else {
                return {
                  ...book,
                  primaryFile: undefined,
                  alternativeFormats: []
                };
              }
            } else {
              return {
                ...book,
                alternativeFormats: book.alternativeFormats?.filter(file => file.id !== fileId)
              };
            }
          }
          return book;
        });

        this.bookStateService.updateBookState({
          ...currentState,
          books: updatedBooks
        });

        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('book.bookService.toast.fileDeletedSummary'),
          detail: this.t.translate('book.bookService.toast.bookFileDeletedDetail')
        });
      }),
      catchError(error => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('book.bookService.toast.fileDeleteFailedSummary'),
          detail: error?.error?.message || error?.message || this.t.translate('book.bookService.toast.fileDeleteFailedDetail')
        });
        return throwError(() => error);
      })
    );
  }

  uploadAdditionalFile(bookId: number, file: File, fileType: AdditionalFileType): Observable<AdditionalFile> {
    const formData = new FormData();
    formData.append('file', file);

    const isBook = fileType === AdditionalFileType.ALTERNATIVE_FORMAT;
    formData.append('isBook', String(isBook));

    if (isBook) {
      const lower = (file?.name || '').toLowerCase();
      const ext = lower.includes('.') ? lower.substring(lower.lastIndexOf('.') + 1) : '';
      const EXTENSION_TO_BOOK_TYPE: Record<string, string> = {
        pdf: 'PDF',
        epub: 'EPUB',
        cbz: 'CBX', cbr: 'CBX', cb7: 'CBX',
        mobi: 'MOBI',
        azw3: 'AZW3', azw: 'AZW3',
        fb2: 'FB2',
        m4b: 'AUDIOBOOK', m4a: 'AUDIOBOOK', mp3: 'AUDIOBOOK', opus: 'AUDIOBOOK',
      };
      const bookType = EXTENSION_TO_BOOK_TYPE[ext] ?? null;

      if (bookType) {
        formData.append('bookType', bookType);
      }
    }
    return this.http.post<AdditionalFile>(`${this.url}/${bookId}/files`, formData).pipe(
      tap((newFile) => {
        const currentState = this.bookStateService.getCurrentBookState();
        const updatedBooks = (currentState.books || []).map(book => {
          if (book.id === bookId) {
            const updatedBook = {...book};
            if (fileType === AdditionalFileType.ALTERNATIVE_FORMAT) {
              updatedBook.alternativeFormats = [...(book.alternativeFormats || []), newFile];
            } else {
              updatedBook.supplementaryFiles = [...(book.supplementaryFiles || []), newFile];
            }
            return updatedBook;
          }
          return book;
        });

        this.bookStateService.updateBookState({
          ...currentState,
          books: updatedBooks
        });

        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('book.bookService.toast.fileUploadedSummary'),
          detail: this.t.translate('book.bookService.toast.fileUploadedDetail')
        });
      }),
      catchError(error => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('book.bookService.toast.uploadFailedSummary'),
          detail: error?.error?.message || error?.message || this.t.translate('book.bookService.toast.uploadFailedDetail')
        });
        return throwError(() => error);
      })
    );
  }

  downloadAdditionalFile(book: Book, fileId: number): void {
    const additionalFile = [
      ...(book.alternativeFormats || []),
      ...(book.supplementaryFiles || [])
    ].find((f: AdditionalFile) => f.id === fileId);
    const downloadUrl = `${this.url}/${book.id}/files/${fileId}/download`;
    this.fileDownloadService.downloadFile(downloadUrl, additionalFile?.fileName ?? 'file');
  }

  detachBookFile(bookId: number, fileId: number, copyMetadata: boolean): Observable<DetachBookFileResponse> {
    return this.http.post<DetachBookFileResponse>(`${this.url}/${bookId}/files/${fileId}/detach`, { copyMetadata }).pipe(
      tap(response => {
        const currentState = this.bookStateService.getCurrentBookState();
        let updatedBooks = (currentState.books || []).map(book =>
          book.id === bookId ? response.sourceBook : book
        );
        updatedBooks = [...updatedBooks, response.newBook];

        this.bookStateService.updateBookState({
          ...currentState,
          books: updatedBooks
        });

        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('metadata.viewer.toast.detachFileSuccessSummary'),
          detail: this.t.translate('metadata.viewer.toast.detachFileSuccessDetail')
        });
      }),
      catchError(error => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('metadata.viewer.toast.detachFileErrorSummary'),
          detail: error?.error?.message || error?.message || this.t.translate('metadata.viewer.toast.detachFileErrorDetail')
        });
        return throwError(() => error);
      })
    );
  }

  findDuplicates(request: DuplicateDetectionRequest): Observable<DuplicateGroup[]> {
    return this.http.post<DuplicateGroup[]>(`${this.url}/duplicates`, request);
  }

  attachBookFiles(targetBookId: number, sourceBookIds: number[], moveFiles: boolean): Observable<{updatedBook: Book, deletedSourceBookIds: number[]}> {
    return this.http.post<{updatedBook: Book, deletedSourceBookIds: number[]}>(`${this.url}/${targetBookId}/attach-file`, {
      sourceBookIds,
      moveFiles
    }).pipe(
      tap(response => {
        const currentState = this.bookStateService.getCurrentBookState();
        const deletedIdSet = new Set(response.deletedSourceBookIds);
        let updatedBooks = (currentState.books || []).map(book =>
          book.id === targetBookId ? response.updatedBook : book
        ).filter(book => !deletedIdSet.has(book.id));

        this.bookStateService.updateBookState({
          ...currentState,
          books: updatedBooks
        });

        const fileCount = sourceBookIds.length;
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('book.bookService.toast.filesAttachedSummary'),
          detail: this.t.translate('book.bookService.toast.filesAttachedDetail', {count: fileCount})
        });
      }),
      catchError(error => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('book.bookService.toast.attachmentFailedSummary'),
          detail: error?.error?.message || error?.message || this.t.translate('book.bookService.toast.attachmentFailedDetail')
        });
        return throwError(() => error);
      })
    );
  }
}
