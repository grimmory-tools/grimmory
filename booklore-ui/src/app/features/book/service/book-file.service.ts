import {inject, Injectable} from '@angular/core';
import {Observable, throwError} from 'rxjs';
import {HttpClient} from '@angular/common/http';
import {catchError, tap} from 'rxjs/operators';
import {AdditionalFile, AdditionalFileType, Book} from '../model/book.model';
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

  uploadAdditionalFile(bookId: number, file: File, fileType: AdditionalFileType, description?: string): Observable<AdditionalFile> {
    const formData = new FormData();
    formData.append('file', file);

    const isBook = fileType === AdditionalFileType.ALTERNATIVE_FORMAT;
    formData.append('isBook', String(isBook));

    if (isBook) {
      const lower = (file?.name || '').toLowerCase();
      const ext = lower.includes('.') ? lower.substring(lower.lastIndexOf('.') + 1) : '';
      const bookType = ext === 'pdf'
        ? 'PDF'
        : ext === 'epub'
          ? 'EPUB'
          : (ext === 'cbz' || ext === 'cbr' || ext === 'cb7' || ext === 'cbt')
            ? 'CBX'
            : (ext === 'm4b' || ext === 'm4a' || ext === 'mp3')
              ? 'AUDIOBOOK'
              : null;

      if (bookType) {
        formData.append('bookType', bookType);
      }
    }
    if (description) {
      formData.append('description', description);
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

  attachBookFiles(targetBookId: number, sourceBookIds: number[], deleteSourceBooks: boolean): Observable<Book> {
    return this.http.post<Book>(`${this.url}/${targetBookId}/attach-file`, {
      sourceBookIds,
      deleteSourceBooks
    }).pipe(
      tap(updatedBook => {
        const currentState = this.bookStateService.getCurrentBookState();
        let updatedBooks = (currentState.books || []).map(book =>
          book.id === targetBookId ? updatedBook : book
        );

        if (deleteSourceBooks) {
          const sourceIdSet = new Set(sourceBookIds);
          updatedBooks = updatedBooks.filter(book => !sourceIdSet.has(book.id));
        }

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
